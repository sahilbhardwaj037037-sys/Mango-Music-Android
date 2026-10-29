"""Download-only adapter. Separate from streaming format selection and playback resolution."""
import json
import os
import re
import tempfile
from mango_online import options, GuardedYoutubeDL, Cancelled

def download(ident, folder, qjs, cancelled, progress, max_bytes, reserve_bytes, temp_root):
    try:
        if not re.fullmatch(r'[A-Za-z0-9_-]{11}', ident): raise ValueError()
        os.environ['LD_LIBRARY_PATH']=os.path.dirname(qjs)
        tempfile.tempdir=temp_root
        os.environ["TMPDIR"]=temp_root
        def hook(event):
            if cancelled.get(): raise Cancelled()
            done=event.get('downloaded_bytes') or 0
            total=event.get('total_bytes') or event.get('total_bytes_estimate') or 0
            if done>max_bytes or total>max_bytes: raise RuntimeError('mango_size')
            if os.statvfs(folder).f_bavail*os.statvfs(folder).f_frsize<reserve_bytes: raise RuntimeError('mango_storage')
            progress.update(int(done),int(total))
        opts=options(qjs,cancelled)
        opts.update({'skip_download':False,'outtmpl':os.path.join(folder,'audio.%(ext)s'),
                     'format':'bestaudio[protocol=https][ext=m4a]/bestaudio[protocol=https][ext=webm]/bestaudio[protocol^=m3u8]',
                     'hls_prefer_native':True,'fixup':'never','continuedl':False,
                     'skip_unavailable_fragments':False,'fragment_retries':1,
                     'progress_hooks':[hook], 'allow_unplayable_formats':False,
                     'writesubtitles':False,'writeinfojson':False,'writethumbnail':False})
        with GuardedYoutubeDL(opts) as ydl:
            info=ydl.extract_info('https://www.youtube.com/watch?v='+ident,download=False)
            if info.get('availability') in ('private','premium_only','subscriber_only','needs_auth') or info.get('has_drm') or info.get('is_downloadable') is False or info.get('downloadable') is False:
                return json.dumps({'error':'restricted'})
            if info.get('is_live') or info.get('live_status') in ('is_live','is_upcoming') or info.get('vcodec') not in ('none',None):
                return json.dumps({'error':'unsupported'})
            size=info.get('filesize') or info.get('filesize_approx') or 0
            if size>max_bytes:return json.dumps({'error':'size'})
            ydl.process_info(info)
            if cancelled.get():raise Cancelled()
            filename=info.get('filepath') or info.get('_filename') or ydl.prepare_filename(info)
            if not os.path.isfile(filename):
                files=[os.path.join(folder,f) for f in os.listdir(folder) if f.startswith('audio.') and not f.endswith(('.part','.ytdl','.ytdlp','.temp'))]
                if len(files)!=1:return json.dumps({'error':'unsupported'})
                filename=files[0]
            return json.dumps({'path':filename})
    except Cancelled:return json.dumps({'error':'cancelled'})
    except Exception as error:
        text=str(error).lower()
        if 'mango_storage' in text or 'no space' in text:kind='storage'
        elif 'mango_size' in text:kind='size'
        elif any(s in text for s in ('drm','sign in','login','private','members-only','age-restrict','not available in your country','not a bot','download disabled','downloads are disabled','403','401')):kind='restricted'
        elif any(s in text for s in ('requested format','ffmpeg','unsupported')):kind='unsupported'
        else:kind='network'
        return json.dumps({'error':kind})
