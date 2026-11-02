"""Pinned anonymous yt-dlp adapter. No downloads, credentials or access-restriction workarounds."""
import os
import tempfile
import json
import re
import subprocess
import sys
import time
import socket
import yt_dlp

_orig_getaddrinfo = socket.getaddrinfo
_dns_cache = {}

def _fallback_dns(host):
    if host in _dns_cache:
        return _dns_cache[host]
    for dns_ip in ("8.8.8.8", "1.1.1.1"):
        try:
            import random
            qid = random.randint(1, 65535)
            header = qid.to_bytes(2, "big") + b"\x01\x00\x00\x01\x00\x00\x00\x00\x00\x00"
            qname = b"".join(len(part).to_bytes(1, "big") + part.encode() for part in host.split(".")) + b"\x00"
            packet = header + qname + b"\x00\x01\x00\x01"
            s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            s.settimeout(2.0)
            s.sendto(packet, (dns_ip, 53))
            data, _ = s.recvfrom(512)
            s.close()
            idx = 12
            while data[idx] != 0:
                if (data[idx] & 0xC0) == 0xC0:
                    idx += 2
                    break
                idx += 1 + data[idx]
            else:
                idx += 1
            idx += 4
            ancount = int.from_bytes(data[6:8], "big")
            for _ in range(ancount):
                if idx >= len(data): break
                if (data[idx] & 0xC0) == 0xC0:
                    idx += 2
                else:
                    while data[idx] != 0: idx += 1 + data[idx]
                    idx += 1
                rtype = int.from_bytes(data[idx:idx+2], "big")
                rdlength = int.from_bytes(data[idx+8:idx+10], "big")
                idx += 10
                if rtype == 1 and rdlength == 4:
                    ip = ".".join(str(b) for b in data[idx:idx+4])
                    _dns_cache[host] = ip
                    return ip
                idx += rdlength
        except Exception:
            continue
    return None

def _guarded_getaddrinfo(host, port, family=0, type=0, proto=0, flags=0):
    try:
        return _orig_getaddrinfo(host, port, family, type, proto, flags)
    except socket.gaierror:
        if isinstance(host, str) and not host.replace('.', '').isdigit():
            ip = _fallback_dns(host)
            if ip:
                return _orig_getaddrinfo(ip, port, family, type, proto, flags)
        raise

socket.getaddrinfo = _guarded_getaddrinfo

class Cancelled(Exception):
    pass

class GuardedYoutubeDL(yt_dlp.YoutubeDL):
    def urlopen(self, request):
        if self.params['_mango_cancelled'].get(): raise Cancelled()
        return super().urlopen(request)

class QuietLogger:
    def __init__(self, cancelled):
        self.cancelled = cancelled
    def check(self):
        if self.cancelled.get():
            raise Cancelled()
    def debug(self, message): self.check()
    def warning(self, message): self.check()
    def error(self, message): self.check()

def options(qjs, cancelled):
    logger = QuietLogger(cancelled)
    return {
        'quiet': True, 'no_warnings': True, 'logger': logger, '_mango_cancelled': cancelled,
        'source_address': '0.0.0.0',
        'socket_timeout': 20, 'retries': 1, 'extractor_retries': 1,
        'noplaylist': True, 'skip_download': True, 'cachedir': False,
        'js_runtimes': {'quickjs': {'path': qjs}}, 'remote_components': [],
        'extractor_args': {'youtube': {'player_client': ['android', 'web']}},
        'progress_hooks': [lambda event: logger.check()],
    }

def track(info):
    ident = info.get('id', '')
    if not re.fullmatch(r'[A-Za-z0-9_-]{11}', ident): return None
    title = info.get('title')
    if not title: return None
    thumbs = info.get('thumbnails') or []
    thumbnail = info.get('thumbnail')
    if not thumbnail and thumbs:
        thumbnail = next((t.get('url') for t in reversed(thumbs) if str(t.get('url','')).startswith('https://')), None)
    duration = info.get('duration') or 0
    return {'id': 'youtube:' + ident, 'title': title,
            'artist': info.get('artist') or info.get('channel') or info.get('uploader') or 'YouTube',
            'uri': 'https://www.youtube.com/watch?v=' + ident,
            'durationMs': int(max(0, duration) * 1000), 'thumbnailUrl': thumbnail}

def execute(operation, value, qjs, cancelled, temp_root):
    os.environ["LD_LIBRARY_PATH"] = os.path.dirname(qjs)
    tempfile.tempdir = temp_root
    os.environ["TMPDIR"] = temp_root
    start = time.monotonic()
    try:
        if operation == 'diagnostics':
            version = subprocess.run([qjs, '--version'], capture_output=True, text=True, timeout=5)
            return json.dumps({'python': sys.version.split()[0], 'ytDlp': yt_dlp.version.__version__, 'quickJs': version.stdout.strip(), 'quickJsExit': version.returncode})
        opts = options(qjs, cancelled)
        if operation == 'search':
            query = value.strip()
            if not query or len(query)>200 or any(ord(c)<32 for c in query): raise ValueError()
            opts['extract_flat'] = 'in_playlist'
            with GuardedYoutubeDL(opts) as ydl:
                data = ydl.extract_info('ytsearch10:' + query, download=False)
            entries=[]; seen=set()
            for entry in data.get('entries') or []:
                item=track(entry)
                if item and item['id'] not in seen:
                    entries.append(item);seen.add(item['id'])
            result={'tracks': entries}
        elif operation == 'mix':
            if not re.fullmatch(r'[A-Za-z0-9_-]{11}', value): raise ValueError()
            opts.update({'noplaylist': False, 'extract_flat': 'in_playlist',
                         'playlistend': 20, 'lazy_playlist': True})
            with GuardedYoutubeDL(opts) as ydl:
                data = ydl.extract_info('https://www.youtube.com/watch?v=' + value + '&list=RD' + value, download=False)
                entries=[]; seen=set()
                for entry in data.get('entries') or []:
                    if cancelled.get(): raise Cancelled()
                    if entry.get('availability') in ('private','premium_only','subscriber_only','needs_auth'): continue
                    if entry.get('live_status') in ('is_live','is_upcoming'): continue
                    item=track(entry)
                    if item and item['id'] not in seen:
                        entries.append(item);seen.add(item['id'])
                    if len(entries)>=20: break
            result={'tracks': entries, 'source': 'radio'}
        elif operation == 'resolve':
            if not re.fullmatch(r'[A-Za-z0-9_-]{11}', value): raise ValueError()
            opts['format'] = 'bestaudio[protocol^=m3u8]/bestaudio[ext=m4a]/bestaudio[ext=webm]/bestaudio/best[acodec!=none]'
            with GuardedYoutubeDL(opts) as ydl:
                data=ydl.extract_info('https://www.youtube.com/watch?v=' + value, download=False)
            if data.get('availability') in ('private','premium_only','subscriber_only','needs_auth'):
                return json.dumps({'error':'restricted'})
            url=data.get('url','')
            acodec=data.get('acodec')
            if not url.startswith('https://') or acodec in ('none', None):
                return json.dumps({'error':'unavailable'})
            protocol=data.get('protocol','')
            mime='application/x-mpegURL' if 'm3u8' in protocol else ('audio/webm' if data.get('ext')=='webm' else 'audio/mp4')
            result={'url':url,'mimeType':mime,'headers':data.get('http_headers') or {},'track':track(data)}
        else: raise ValueError()
        if cancelled.get():raise Cancelled()
        result['elapsedMs']=int((time.monotonic()-start)*1000)
        return json.dumps(result)
    except Cancelled:
        return json.dumps({'error':'cancelled'})
    except Exception as error:
        # Inspect for classification only. Never return/log raw extractor URLs, headers or tokens.
        text=str(error).lower()
        if any(word in text for word in ('private','members-only','age-restrict','not available in your country','georestricted','confirm your age','premium_only')):
            category='restricted'
        elif any(word in text for word in ('timed out','timeout')):category='timeout'
        elif any(word in text for word in ('unable to download','network','resolve','connection')):category='network'
        else:category='unavailable'
        return json.dumps({'error':category})
