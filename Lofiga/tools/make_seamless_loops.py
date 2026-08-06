#!/usr/bin/env python3
"""Make the atmosphere loop WAVs seamless for MediaPlayer's hard loop.

MediaPlayer sets isLooping = true, which jumps from the last sample straight
back to the first. The four 5.00s atmosphere assets have large discontinuities
at that point (max boundary jump 6075..27600 of the 16-bit range), so every
~5s the loop seam is audible as a click ("atmosphere stops then restarts").

This crossfades the head into the tail: the last F samples become an
equal-power blend of the original tail and the head region. The fade starts
exactly at the original tail sample (smooth entry) and the fade length F is
chosen so the final sample best matches the first sample, shrinking the
loop-point jump from full-scale to roughly adjacent-sample level.

The rest of the file is untouched, so duration/level/frequency content are
preserved.

Usage: python3 tools/make_seamless_loops.py [path/to/atmosphere/dir]
"""

import argparse
import array
import math
import pathlib
import sys
import wave

F_MIN = 2000   # ~45 ms
F_MAX = 8000   # ~181 ms


def read_wav(path):
    with wave.open(str(path), "rb") as w:
        nch = w.getnchannels()
        sw = w.getsampwidth()
        rate = w.getframerate()
        frames = w.getnframes()
        raw = w.readframes(frames)
    if sw != 2:
        raise SystemExit(f"{path}: expected 16-bit PCM, got {sw * 8}-bit")
    if nch != 1:
        raise SystemExit(f"{path}: expected mono, got {nch} channels")
    samples = array.array("h")
    samples.frombytes(raw)
    if sys.byteorder != "little":
        samples.byteswap()
    return samples, rate


def write_wav(path, samples, rate):
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(rate)
        w.writeframes(samples.tobytes())


def seam_score(samples):
    """Max |first[i] - mirrored tail[i]| over the first 40 samples — the metric
    used to detect the original hard-loop discontinuity."""
    n = len(samples)
    return max(abs(samples[i] - samples[n - 1 - i]) for i in range(40))


def make_seamless(samples):
    n = len(samples)
    best_f = min(
        range(F_MIN, min(F_MAX, n) + 1),
        key=lambda f: abs(samples[f - 1] - samples[0]),
    )
    out = array.array("h", samples)
    for i in range(best_f):
        frac = i / (best_f - 1)
        g_tail = math.cos(frac * math.pi / 2)
        g_head = math.sin(frac * math.pi / 2)
        v = samples[n - best_f + i] * g_tail + samples[i] * g_head
        out[n - best_f + i] = max(-32768, min(32767, int(v)))
    return out, best_f


def main():
    default_dir = (
        pathlib.Path(__file__).resolve().parent.parent
        / "native-android/app/src/main/assets/atmosphere"
    )
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("dir", nargs="?", default=str(default_dir))
    args = ap.parse_args()
    d = pathlib.Path(args.dir)
    for path in sorted(d.glob("*.wav")):
        samples, rate = read_wav(path)
        before = seam_score(samples)
        out, f = make_seamless(samples)
        after = seam_score(out)
        loop_jump = abs(out[-1] - out[0])
        write_wav(path, out, rate)
        print(
            f"{path.name}: frames={len(out)} fade={f} ({f / rate * 1000:.0f}ms) "
            f"seam {before} -> {after} (loop-point jump {loop_jump})"
        )


if __name__ == "__main__":
    main()
