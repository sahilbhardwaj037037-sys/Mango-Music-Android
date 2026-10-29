"""Pinned anonymous yt-dlp adapter. No downloads, credentials or access-restriction workarounds."""
import os
import tempfile
import json
import re
import subprocess
import sys
import time
import yt_dlp

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
        'socket_timeout': 12, 'retries': 1, 'extractor_retries': 1,
        'noplaylist': True, 'skip_download': True, 'cachedir': False,
        'js_runtimes': {'quickjs': {'path': qjs}}, 'remote_components': [],
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
        elif operation == 'resolve':
            if not re.fullmatch(r'[A-Za-z0-9_-]{11}', value): raise ValueError()
            opts['format'] = 'bestaudio[protocol^=m3u8]/bestaudio[ext=m4a]/bestaudio[ext=webm]/bestaudio'
            with GuardedYoutubeDL(opts) as ydl:
                data=ydl.extract_info('https://www.youtube.com/watch?v=' + value, download=False)
            if data.get('availability') in ('private','premium_only','subscriber_only','needs_auth'):
                return json.dumps({'error':'restricted'})
            url=data.get('url','')
            if not url.startswith('https://') or data.get('vcodec') not in ('none', None):
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
        if any(word in text for word in ('sign in','login','private','members-only','age-restrict','not available in your country','not a bot','confirm your age')):
            category='restricted'
        elif any(word in text for word in ('timed out','timeout')):category='timeout'
        elif any(word in text for word in ('unable to download','network','resolve','connection')):category='network'
        else:category='unavailable'
        return json.dumps({'error':category})
