from pathlib import Path
import hashlib,zipfile,io
root=Path(__file__).resolve().parent.parent
folder=root/'artifacts'
parts=sorted(folder.glob('SparkBackground-0.6.apk.zip.part*'))
if not parts: raise SystemExit('Download the complete repository ZIP first')
data=b''.join(p.read_bytes() for p in parts)
checks={line.split()[1]:line.split()[0] for line in (folder/'SHA256SUMS-0.6.txt').read_text().splitlines()}
assert hashlib.sha256(data).hexdigest()==checks['SparkBackground-0.6.apk.zip'], 'Archive checksum mismatch'
with zipfile.ZipFile(io.BytesIO(data)) as z: apk=z.read('SparkBackground-0.6.apk')
assert hashlib.sha256(apk).hexdigest()==checks['SparkBackground-0.6.apk'], 'APK checksum mismatch'
output=folder/'SparkBackground-0.6.apk';output.write_bytes(apk);print(output)
