"""Generate original 24-second music loops. No third-party audio or artwork."""
import math, wave, struct
from pathlib import Path
out = Path(__file__).resolve().parents[1] / 'app/src/main/assets'
out.mkdir(exist_ok=True)
for name, notes in [('mango_dawn', [261.63,329.63,392,493.88]), ('pink_orbit',[220,261.63,329.63,392]), ('after_rain',[196,246.94,293.66,369.99])]:
    with wave.open(str(out / (name+'.wav')), 'wb') as w:
        w.setparams((1,2,22050,0,'NONE','not compressed'))
        data=bytearray()
        for i in range(24*22050):
            t=i/22050; beat=t%0.5
            note=notes[int(t*2)%4]
            lead=math.sin(2*math.pi*note*t)*math.exp(-beat*6)*0.22
            pad=sum(math.sin(2*math.pi*n/2*t) for n in notes)*0.025
            kick=math.sin(2*math.pi*(55*beat+3*(1-math.exp(-beat*30))))*math.exp(-beat*25)*0.18
            envelope=min(1,t/0.1,(24-t)/0.3)
            data.extend(struct.pack('<h',int((lead+pad+kick)*envelope*32767)))
        w.writeframes(data)
