#!/usr/bin/env python3
"""Local feasibility probe. Images/overlays stay ignored; counts are not ground truth."""
import argparse, json, time
from pathlib import Path
import cv2
import numpy as np

parser = argparse.ArgumentParser()
parser.add_argument('folder', type=Path)
parser.add_argument('--output', type=Path, default=Path('docs/local-validation'))
args = parser.parse_args()
args.output.mkdir(parents=True, exist_ok=True)
results = []
for path in sorted(args.folder.glob('*.jpeg')):
    image = cv2.imread(str(path))
    h, w = image.shape[:2]
    gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
    gray = cv2.medianBlur(gray, 5)
    started = time.perf_counter()
    circles = cv2.HoughCircles(gray, cv2.HOUGH_GRADIENT, 1.2, min(h,w)/60,
        param1=100, param2=30, minRadius=max(6,min(h,w)//120), maxRadius=min(h,w)//5)
    candidates = [] if circles is None else sorted(circles[0].tolist(), key=lambda c: -c[2])
    kept=[]
    for x,y,r in candidates:
        if not any(np.hypot(x-a,y-b) < min(r,c)*0.6 for a,b,c in kept):
            kept.append((x,y,r))
    elapsed=round((time.perf_counter()-started)*1000)
    for i,(x,y,r) in enumerate(sorted(kept,key=lambda c:(c[1],c[0])),1):
        cv2.circle(image,(round(x),round(y)),round(r),(0,255,255),2)
        cv2.putText(image,str(i),(round(x),round(y)),cv2.FONT_HERSHEY_SIMPLEX,.5,(0,0,255),2)
    cv2.imwrite(str(args.output / (path.stem+'-hough.jpg')),image)
    results.append({'file':path.name,'count':len(kept),'millis':elapsed,'ground_truth':'unverified','coordinates':kept})
(args.output/'probe.json').write_text(json.dumps(results,ensure_ascii=False,indent=2))
print(json.dumps([{k:v for k,v in r.items() if k!='coordinates'} for r in results],ensure_ascii=False))
