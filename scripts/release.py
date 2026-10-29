#!/usr/bin/env python3
"""Keychain-backed release signing. Passwords never enter argv, source, Gradle properties or logs."""
from pathlib import Path
import argparse
import ctypes
import datetime
import hashlib
import json
import os
import secrets
import re
import struct
import zipfile
import subprocess
import sys
import zoneinfo

ROOT=Path(__file__).resolve().parents[1]
SIGNING=ROOT/'.local/signing'
META=SIGNING/'release.json'
SERVICE=b'MangoMusic.Android.ReleaseSigning'
ACCOUNT=b'com.mangomusic'
JAVA=Path(os.environ.get('JAVA_HOME','/Applications/Android Studio.app/Contents/jbr/Contents/Home'))
BUILD_FILE=(ROOT/'app/build.gradle.kts').read_text()
VERSION=re.search(r'versionName\s*=\s*"([0-9]+(?:\.[0-9]+){1,2})"',BUILD_FILE).group(1)
VERSION_CODE=int(re.search(r'versionCode\s*=\s*(\d+)',BUILD_FILE).group(1))
DISTRIBUTION=ROOT/'.local/releases'/f'v{VERSION}'/f'Mango-Music-Android-v{VERSION}-release.apk'


def keychain(password=None):
    """Use Security.framework in memory: never put a password in security(1)'s argv."""
    sec=ctypes.CDLL('/System/Library/Frameworks/Security.framework/Security')
    length=ctypes.c_uint32();data=ctypes.c_void_p()
    find=sec.SecKeychainFindGenericPassword
    find.argtypes=[ctypes.c_void_p,ctypes.c_uint32,ctypes.c_char_p,ctypes.c_uint32,ctypes.c_char_p,ctypes.POINTER(ctypes.c_uint32),ctypes.POINTER(ctypes.c_void_p),ctypes.c_void_p]
    status=find(None,len(SERVICE),SERVICE,len(ACCOUNT),ACCOUNT,ctypes.byref(length),ctypes.byref(data),None)
    if password is None:
        if status:raise RuntimeError(f'Keychain access failed ({status}). Unlock your login keychain; do not recreate the signing key.')
        try:return ctypes.string_at(data,length.value).decode()
        finally:
            sec.SecKeychainItemFreeContent.argtypes=[ctypes.c_void_p,ctypes.c_void_p]
            sec.SecKeychainItemFreeContent(None,data)
    if status==0:
        sec.SecKeychainItemFreeContent.argtypes=[ctypes.c_void_p,ctypes.c_void_p]
        sec.SecKeychainItemFreeContent(None,data)
        raise RuntimeError('Signing password already exists in Keychain. Refusing to overwrite it.')
    if status!=-25300:raise RuntimeError(f'Cannot check Keychain ({status}). No key was created.')
    encoded=password.encode()
    add=sec.SecKeychainAddGenericPassword
    add.argtypes=[ctypes.c_void_p,ctypes.c_uint32,ctypes.c_char_p,ctypes.c_uint32,ctypes.c_char_p,ctypes.c_uint32,ctypes.c_char_p,ctypes.c_void_p]
    status=add(None,len(SERVICE),SERVICE,len(ACCOUNT),ACCOUNT,len(encoded),encoded,None)
    if status:raise RuntimeError(f'Cannot save signing credentials to Keychain ({status}). No key was created.')


def private_dir(path):
    path.mkdir(parents=True,exist_ok=True);path.chmod(0o700)


def private_write(path,content):
    # Exclusive creation prevents an existing identity or recovery copy being replaced.
    with os.fdopen(os.open(path,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600),'wb') as output:
        output.write(content);output.flush();os.fsync(output.fileno())


def keytool(args,password):
    env=os.environ.copy();env['MANGO_KEYTOOL_PASSWORD']=password
    subprocess.run([str(JAVA/'bin/keytool'),*args],env=env,check=True,stdout=subprocess.PIPE,stderr=subprocess.PIPE)


def metadata():
    if not META.is_file():raise RuntimeError('Release signing metadata is missing. Restore the existing identity; do not generate a replacement.')
    return json.loads(META.read_text())


def verify_identity(info,password):
    pin=ROOT/'release/signing-certificate.json'
    if not pin.is_file() or json.loads(pin.read_text())['certificateSha256']!=info['certificateSha256']:
        raise RuntimeError('Signing identity differs from the version-controlled public certificate. Restore the original identity; do not replace it.')
    original=Path(info['keystore']);backup=Path(info['backup'])
    if not original.is_file() or not backup.is_file():raise RuntimeError('Release key or backup missing. Restore the original key; never replace it.')
    if hashlib.sha256(original.read_bytes()).hexdigest()!=info['keystoreSha256'] or original.read_bytes()!=backup.read_bytes():
        raise RuntimeError('Signing key/backup checksum changed. Stop and investigate.')
    for number,path in enumerate((original,backup)):
        cert=SIGNING/f'verify-certificate-{number}.der'
        # Public certificate and certificate request contain no private key/password.
        keytool(['-exportcert','-keystore',str(path),'-storetype','PKCS12','-alias',info['alias'],'-storepass:env','MANGO_KEYTOOL_PASSWORD','-file',str(cert)],password)
        if hashlib.sha256(cert.read_bytes()).hexdigest()!=info['certificateSha256']:
            raise RuntimeError('Release certificate does not match the recorded identity.')
        keytool(['-certreq','-keystore',str(path),'-storetype','PKCS12','-alias',info['alias'],'-storepass:env','MANGO_KEYTOOL_PASSWORD','-keypass:env','MANGO_KEYTOOL_PASSWORD','-file',str(SIGNING/f'verify-private-key-{number}.csr')],password)
    return info


def initialize():
    if (ROOT/'release/signing-certificate.json').exists():
        raise RuntimeError('This project already has a published signing identity. Restore that original key; never create a replacement on a new checkout.')
    private_dir(SIGNING)
    if META.exists() or list(SIGNING.glob('*.keystore')) or list(SIGNING.glob('*.p12')):
        raise RuntimeError('Release signing material already exists. Refusing to replace or generate a second identity. Use verify-identity.')
    password=secrets.token_urlsafe(48)
    keychain(password)
    if keychain()!=password:raise RuntimeError('Keychain round-trip failed; no key was created.')
    target=SIGNING/'mango-music-release.keystore'
    alias='mango-music-release'
    # PKCS12 encrypts the long-term private key; env passwords stay out of command arguments.
    keytool(['-genkeypair','-keystore',str(target),'-storetype','PKCS12','-alias',alias,'-keyalg','RSA','-keysize','3072','-sigalg','SHA256withRSA','-validity','10000','-dname','CN=Mango Music, O=Mango Music','-storepass:env','MANGO_KEYTOOL_PASSWORD','-keypass:env','MANGO_KEYTOOL_PASSWORD'],password)
    target.chmod(0o600)
    stamp=datetime.datetime.now(zoneinfo.ZoneInfo('Asia/Kolkata')).strftime('%Y%m%d-%H%M%S%z')
    folder=ROOT/'.local/signing-backups'/stamp;private_dir(folder.parent);private_dir(folder)
    backup=folder/target.name;private_write(backup,target.read_bytes())
    certificate=SIGNING/'release-certificate.der'
    keytool(['-exportcert','-keystore',str(target),'-alias',alias,'-storepass:env','MANGO_KEYTOOL_PASSWORD','-file',str(certificate)],password)
    info={'package':'com.mangomusic','alias':alias,'storeType':'PKCS12','keyAlgorithm':'RSA-3072','keystore':str(target),'backup':str(backup),'keychainService':SERVICE.decode(),'keychainAccount':ACCOUNT.decode(),'keystoreSha256':hashlib.sha256(target.read_bytes()).hexdigest(),'certificateSha256':hashlib.sha256(certificate.read_bytes()).hexdigest(),'created':stamp}
    private_write(META,(json.dumps(info,indent=2)+'\n').encode())
    private_write(folder/'release.json',META.read_bytes())
    pin=ROOT/'release/signing-certificate.json'
    pin.parent.mkdir(exist_ok=True)
    if not pin.exists():private_write(pin,(json.dumps({k:info[k] for k in ('package','alias','storeType','keyAlgorithm','certificateSha256')},indent=2)+'\n').encode())
    verify_identity(info,password)
    print(json.dumps(info,indent=2))  # Public fingerprints and paths only; never password/key bytes.


def signing_environment():
    info=metadata();password=keychain();verify_identity(info,password)
    env=os.environ.copy();env.update(MANGO_RELEASE_KEYSTORE=info['keystore'],MANGO_RELEASE_ALIAS=info['alias'],MANGO_RELEASE_PASSWORD=password)
    return env


def verify_apk(path):
    env=os.environ.copy();env['JAVA_HOME']=str(JAVA)
    tools=ROOT/'.tools/sdk/build-tools/36.0.0'
    result=subprocess.run([str(tools/'apksigner'),'verify','--verbose','--print-certs',str(path)],env=env,check=True,capture_output=True,text=True)
    info=metadata()
    if f"Signer #1 certificate SHA-256 digest: {info['certificateSha256']}" not in result.stdout:
        raise RuntimeError('APK signer differs from the long-term release certificate.')
    subprocess.run([str(tools/'zipalign'),'-c','-P','16','4',str(path)],check=True,capture_output=True)
    badging=subprocess.run([str(tools/'aapt2'),'dump','badging',str(path)],env=env,check=True,capture_output=True,text=True).stdout
    if f"package: name='com.mangomusic' versionCode='{VERSION_CODE}' versionName='{VERSION}'" not in badging or 'application-debuggable' in badging:
        raise RuntimeError('APK package/version/non-debuggable checks failed.')
    if "native-code: 'arm64-v8a'" not in badging or "native-code: 'arm64-v8a' '" in badging:
        raise RuntimeError('APK must advertise only the supported ARM64 runtime.')
    with zipfile.ZipFile(path) as archive:
        native=[n for n in archive.namelist() if n.startswith('lib/') and n.endswith('.so')]
        for name in native:
            data=archive.read(name)
            if data[:5]!=b'\x7fELF\x02':raise RuntimeError('Unexpected native ELF format.')
            offset=struct.unpack_from('<Q',data,32)[0];size,count=struct.unpack_from('<HH',data,54)
            for index in range(count):
                header=struct.unpack_from('<IIQQQQQQ',data,offset+index*size)
                if header[0]==1 and header[-1]<16384:raise RuntimeError('Native library is not 16 KB compatible: '+name)
        provenance=json.loads((ROOT/'online-runtime/runtime-provenance.json').read_text())
        for name,expected in provenance['binaries'].items():
            if hashlib.sha256(archive.read('lib/arm64-v8a/'+name)).hexdigest()!=expected:
                raise RuntimeError('Bundled JavaScript runtime provenance mismatch.')
    print(result.stdout.strip())
    print(json.dumps({'apk':str(path),'bytes':path.stat().st_size,'sha256':hashlib.sha256(path.read_bytes()).hexdigest()},indent=2))


def export_recovery(directory,confirmed):
    """Run manually against an already mounted encrypted off-Mac volume; never to chat/stdout."""
    if not confirmed:raise RuntimeError('Mount an encrypted external volume, then explicitly pass --confirm-encrypted-volume. See docs/RELEASE.md.')
    target=Path(directory).expanduser().resolve()
    if not target.is_relative_to(Path('/Volumes')) or target.parts[2:3]==('Macintosh HD',):
        raise RuntimeError('Choose a dedicated folder on a mounted encrypted external volume under /Volumes.')
    if target.exists():raise RuntimeError('Recovery directory already exists. Choose a new empty destination; no existing backup will be overwritten.')
    password=keychain();info=verify_identity(metadata(),password)
    private_dir(target)
    private_write(target/'mango-music-release.keystore',Path(info['keystore']).read_bytes())
    private_write(target/'release.json',META.read_bytes())
    private_write(target/'signing-credentials.txt',(password+'\n').encode())
    private_write(target/'README.txt',b'Private disaster recovery for com.mangomusic. Keep this directory only on encrypted, offline media. Reuse the original key and alias. Never upload these files or print the credentials. See the repository release guide to restore Keychain on a new Mac.\n')
    print('Recovery copied to encrypted external destination. No credentials printed. Eject and secure the volume.')


def restore_keychain(directory):
    """Restore the same identity on a replacement Mac from the encrypted recovery export."""
    if META.exists() or list(SIGNING.glob('*.keystore')):
        raise RuntimeError('Local signing material already exists. Refusing to replace it.')
    source=Path(directory).expanduser().resolve()
    info=json.loads((source/'release.json').read_text())
    pin=json.loads((ROOT/'release/signing-certificate.json').read_text())
    if info['certificateSha256']!=pin['certificateSha256']:
        raise RuntimeError('Recovery certificate does not match the published release identity.')
    content=(source/'mango-music-release.keystore').read_bytes()
    if hashlib.sha256(content).hexdigest()!=info['keystoreSha256']:
        raise RuntimeError('Recovery keystore checksum mismatch.')
    password=(source/'signing-credentials.txt').read_text().rstrip('\n')
    try:
        existing=keychain()
    except RuntimeError as error:
        if '(-25300)' not in str(error):raise
        existing=None
    if existing is not None and existing!=password:
        raise RuntimeError('Existing Keychain identity differs from recovery. Stop; no credential will be replaced.')
    private_dir(SIGNING)
    original=SIGNING/'mango-music-release.keystore';private_write(original,content)
    stamp=datetime.datetime.now(zoneinfo.ZoneInfo('Asia/Kolkata')).strftime('%Y%m%d-%H%M%S%z')
    folder=ROOT/'.local/signing-backups'/('restored-'+stamp);private_dir(folder.parent);private_dir(folder)
    backup=folder/original.name;private_write(backup,content)
    info.update(keystore=str(original),backup=str(backup))
    verify_identity(info,password)
    if existing is None:keychain(password)  # Reuse an identical credential; never overwrite one.
    private_write(META,(json.dumps(info,indent=2)+'\n').encode())
    private_write(folder/'release.json',META.read_bytes())
    print('Original release identity restored and verified. No credentials printed.')


def main():
    os.umask(0o077)
    parser=argparse.ArgumentParser(description=__doc__)
    sub=parser.add_subparsers(dest='command',required=True)
    sub.add_parser('init-key',help='Explicit one-time creation; refuses any existing key/Keychain entry.')
    sub.add_parser('verify-identity')
    build=sub.add_parser('build')
    run=sub.add_parser('gradle');run.add_argument('arguments',nargs=argparse.REMAINDER)
    verify=sub.add_parser('verify-apk');verify.add_argument('apk',nargs='?',default=str(DISTRIBUTION))
    restore=sub.add_parser('restore-keychain');restore.add_argument('--recovery-directory',required=True)
    export=sub.add_parser('export-recovery');export.add_argument('--directory',required=True);export.add_argument('--confirm-encrypted-volume',action='store_true')
    args=parser.parse_args()
    if args.command=='init-key':initialize()
    elif args.command=='verify-identity':print(json.dumps(verify_identity(metadata(),keychain()),indent=2))
    elif args.command in ('build','gradle'):
        env=signing_environment()
        tasks=['-PmangoReleaseTests=true',':app:assembleRelease',':app:testReleaseUnitTest',':app:lintRelease'] if args.command=='build' else args.arguments
        if not tasks:raise RuntimeError('Specify Gradle tasks.')
        if ':app:connectedReleaseAndroidTest' in tasks:
            serial=env.get('ANDROID_SERIAL')
            if not serial:raise RuntimeError('Release instrumentation requires an explicit test-only ANDROID_SERIAL.')
            adb=Path.home()/'Library/Android/sdk/platform-tools/adb'
            device=subprocess.run([str(adb),'-s',serial,'emu','avd','name'],capture_output=True,text=True,check=True).stdout.splitlines()
            if not device or device[0].strip()!='Mango36ReleaseValidation':
                raise RuntimeError('Release instrumentation is restricted to the separate Mango36ReleaseValidation AVD; never target user data.')
        subprocess.run([str(ROOT/'scripts/gradle-env.sh'),'--no-daemon','--no-configuration-cache',*tasks],env=env,cwd=ROOT,check=True)
        if args.command=='build':
            source=ROOT/'app/build/outputs/apk/release/app-release.apk';verify_apk(source)
            private_dir(DISTRIBUTION.parent)
            if DISTRIBUTION.exists() and DISTRIBUTION.read_bytes()!=source.read_bytes():
                raise RuntimeError('Distributable already exists with different bytes. Preserve it and choose the next version before rebuilding.')
            if not DISTRIBUTION.exists():private_write(DISTRIBUTION,source.read_bytes())
            verify_apk(DISTRIBUTION)
    elif args.command=='verify-apk':verify_apk(Path(args.apk))
    elif args.command=='restore-keychain':restore_keychain(args.recovery_directory)
    elif args.command=='export-recovery':export_recovery(args.directory,args.confirm_encrypted_volume)


if __name__=='__main__':
    try:main()
    except (RuntimeError,subprocess.CalledProcessError) as error:
        # Child command contains only paths/aliases and environment variable names, never passwords.
        sys.exit(str(error))
