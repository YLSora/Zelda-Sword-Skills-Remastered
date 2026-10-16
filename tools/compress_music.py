"""Re-encode OST originals to Minecraft-compatible stereo Ogg Vorbis and verify full decoding."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import tempfile


def decode(ffmpeg, path):
    result = subprocess.run([ffmpeg, "-hide_banner", "-v", "error", "-xerror", "-i", str(path),
                             "-progress", "pipe:1", "-f", "null", "-"],
                            capture_output=True, text=True, check=True)
    times = re.findall(r"out_time_us=(\d+)", result.stdout)
    assert times, f"No decoded audio: {path}"
    return int(times[-1]) / 1_000_000


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("originals", type=Path)
    parser.add_argument("--ffmpeg", default="ffmpeg")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    catalog = (root / "src/main/java/zeldaswordskills_remastered/registry/MusicDiscCatalog.java").read_text()
    discs = re.findall(r'new Disc\("([^"]+)", "([^"]+)", (\d+)\)', catalog)
    destination = root / "src/main/resources/assets/zeldaswordskills_remastered/sounds/music_disc"
    report = []
    with tempfile.TemporaryDirectory(prefix="zss-music-") as temporary:
        for disc_id, title, seconds in discs:
            source = args.originals / (title + ".ogg")
            target = destination / (disc_id + ".ogg")
            encoded = Path(temporary) / target.name
            duration = decode(args.ffmpeg, source)
            assert int(seconds) >= duration and int(seconds) - duration < 2, f"Catalog duration mismatch: {title}"
            subprocess.run([args.ffmpeg, "-hide_banner", "-v", "error", "-xerror", "-i", str(source),
                            "-map", "0:a:0", "-map_metadata", "-1", "-vn", "-af", "aresample=22050,asetpts=N/SR/TB", "-c:a", "libvorbis",
                            "-q:a", "0", "-ar", "22050", "-ac", "2", str(encoded)], check=True)
            encoded_duration = decode(args.ffmpeg, encoded)
            assert abs(encoded_duration - duration) < .1, f"Truncated audio: {title}"
            report.append(dict(id=disc_id, source_bytes=source.stat().st_size,
                               previous_bytes=target.stat().st_size, compressed_bytes=encoded.stat().st_size,
                               seconds=round(duration, 3), decoded_seconds=round(encoded_duration, 3),
                               sha256=hashlib.sha256(encoded.read_bytes()).hexdigest()))
            print(f"{title}: {target.stat().st_size} -> {encoded.stat().st_size} bytes", flush=True)
        assert len(report) == 27, "Unexpected disc count"
        assert sum(entry["compressed_bytes"] for entry in report) < 20_000_000, "Music exceeds the 20 MB budget"
        for entry in report:
            target = destination / (entry["id"] + ".ogg")
            # Copy beside the destination before replacing, including across Windows volumes.
            staged = target.with_suffix(".ogg.tmp")
            staged.write_bytes((Path(temporary) / target.name).read_bytes())
            staged.replace(target)
    result = dict(codec="Vorbis", quality=0, sample_rate=22050, channels=2, tracks=report)
    output = root / "build/reports/music-compression.json"
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    before = sum(entry["previous_bytes"] for entry in report)
    after = sum(entry["compressed_bytes"] for entry in report)
    print(f"Total: {before} -> {after} bytes ({1 - after / before:.1%} smaller). Report: {output}")


if __name__ == "__main__":
    main()
