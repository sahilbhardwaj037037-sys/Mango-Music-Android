#!/usr/bin/env python3
"""Publish the verified release APK using existing Git credentials, never tokens in argv/logs."""
import hashlib
import json
import os
from pathlib import Path
import ssl
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request
import importlib.util

ROOT=Path(__file__).resolve().parents[1]
REPOSITORY='sahilbhardwaj037037-sys/Mango-Music-Android'
TAG='android-v1.0.0'
BASE='https://api.github.com/repos/'+REPOSITORY


def main():
    spec=importlib.util.spec_from_file_location('mango_release',ROOT/'scripts/release.py')
    signing=importlib.util.module_from_spec(spec);spec.loader.exec_module(signing)
    apk=signing.DISTRIBUTION
    signing.verify_apk(apk)
    commit=subprocess.check_output(['git','rev-parse',TAG+'^{}'],cwd=ROOT,text=True).strip()
    remote=subprocess.check_output(['git','ls-remote','origin','refs/heads/main','refs/tags/'+TAG+'^{}'],cwd=ROOT,text=True)
    if any(line.split()[0]!=commit for line in remote.splitlines()) or len(remote.splitlines())!=2:
        raise RuntimeError('Release commit/tag must first be verified on GitHub.')
    env=os.environ.copy();env['GIT_TERMINAL_PROMPT']='0'
    credential=subprocess.run(['git','credential','fill'],input='protocol=https\nhost=github.com\n\n',cwd=ROOT,env=env,text=True,capture_output=True,timeout=20)
    values=dict(line.split('=',1) for line in credential.stdout.splitlines() if '=' in line) if credential.returncode==0 else {}
    token=values.get('password')
    if not token:raise RuntimeError('No GitHub API credential available. APK remains local; no token was requested or printed.')
    tls=ssl.create_default_context(cafile='/etc/ssl/cert.pem')
    class NoRedirect(urllib.request.HTTPRedirectHandler):
        def redirect_request(self,*args,**kwargs):
            raise RuntimeError('Refusing an authenticated API redirect; inspect GitHub repository configuration.')
    opener=urllib.request.build_opener(NoRedirect(),urllib.request.HTTPSHandler(context=tls))
    def request(method,url,payload=None,binary=False):
        if urllib.parse.urlparse(url).hostname not in ('api.github.com','uploads.github.com'):
            raise RuntimeError('Refusing to send release credentials to an unexpected host.')
        headers={'Authorization':'Bearer '+token,'Accept':'application/vnd.github+json','X-GitHub-Api-Version':'2022-11-28','User-Agent':'MangoMusic-release-tool','Content-Type':'application/vnd.android.package-archive' if binary else 'application/json'}
        body=payload if binary else json.dumps(payload).encode() if payload is not None else None
        req=urllib.request.Request(url,data=body,headers=headers,method=method)
        with opener.open(req,timeout=180) as response:return json.load(response)
    try:
        existing=request('GET',BASE+'/releases/tags/'+TAG)
    except urllib.error.HTTPError as error:
        if error.code!=404:raise
        existing=None
    if existing is not None:
        raise RuntimeError('A GitHub release already exists for this tag. Refusing to overwrite it or its assets; inspect it manually.')
    sha=hashlib.sha256(apk.read_bytes()).hexdigest()
    certificate=json.loads((ROOT/'release/signing-certificate.json').read_text())['certificateSha256']
    body=f'''First signed Mango Music Android release: v1.0 (versionCode 1).

Native player with online search, Mix/continuous autoplay, background media controls, favorites/history, permitted audio downloads and offline playback. The save confirmation action is labeled Download; source-permission checks remain in place.

Requires ARM64 Android 8.0+ (API 26); targets Android 16. Built without R8/resource shrinking to preserve the embedded Python/Java runtime bridge. Signed release unit tests and on-device release regressions are documented in [RELEASE.md](https://github.com/{REPOSITORY}/blob/{TAG}/docs/RELEASE.md). Physical-device listening remains a manual check.

Online YouTube audio uses an unofficial yt-dlp integration. Provider restrictions and source/content-owner permissions apply; DRM, authentication and download restrictions are not bypassed.

APK SHA-256: `{sha}`

Signing certificate SHA-256: `{certificate}`

Future updates must use this same release key. A debug-signed installation cannot be updated with this APK; preserve its data and do not uninstall it without a recovery plan.
'''
    draft=request('POST',BASE+'/releases',{'tag_name':TAG,'target_commitish':commit,'name':'Mango Music Android v1.0','body':body,'draft':True,'prerelease':False})
    upload=draft['upload_url'].split('{',1)[0]+'?'+urllib.parse.urlencode({'name':apk.name})
    asset=request('POST',upload,apk.read_bytes(),True)
    if asset.get('size')!=apk.stat().st_size or asset.get('digest')!='sha256:'+sha:
        raise RuntimeError('Uploaded asset digest/size did not match. Release remains a draft; local APK is preserved.')
    published=request('PATCH',BASE+'/releases/'+str(draft['id']),{'draft':False})
    confirmed=request('GET',BASE+'/releases/tags/'+TAG)
    assets=confirmed.get('assets',[])
    if confirmed['draft'] or not any(a['name']==apk.name and a.get('digest')=='sha256:'+sha and a['size']==apk.stat().st_size for a in assets):
        raise RuntimeError('Could not verify the published release asset. Inspect the draft/release; local APK is preserved.')
    evidence={'releaseUrl':confirmed['html_url'],'tag':TAG,'commit':commit,'apkSha256':sha,'assetUrl':asset['browser_download_url'],'assetBytes':asset['size'],'assetDigestVerified':True,'draft':confirmed['draft']}
    private=ROOT/'.local/release-validation/github-release.json';private.write_text(json.dumps(evidence,indent=2)+'\n')
    print(json.dumps(evidence,indent=2))


if __name__=='__main__':
    try:main()
    except urllib.error.HTTPError as error:sys.exit(f'GitHub Release API failed (HTTP {error.code}); no credential was printed. Preserve the local APK and inspect any draft release.')
    except urllib.error.URLError:sys.exit('GitHub Release network/TLS request failed; local APK preserved. TLS verification was not disabled.')
    except (RuntimeError,subprocess.CalledProcessError) as error:sys.exit(str(error))
