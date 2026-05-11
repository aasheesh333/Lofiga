import random
import struct
import math
import os

def write_wav(filename, samples, sample_rate=44100):
    num_samples = len(samples)
    byte_rate = sample_rate * 2  # 16-bit mono
    block_align = 2
    data_size = num_samples * block_align
    chunk_size = 36 + data_size

    with open(filename, 'wb') as f:
        # RIFF header
        f.write(b'RIFF')
        f.write(struct.pack('<I', chunk_size))
        f.write(b'WAVE')

        # fmt subchunk
        f.write(b'fmt ')
        f.write(struct.pack('<I', 16))  # Subchunk1Size
        f.write(struct.pack('<H', 1))   # AudioFormat (PCM)
        f.write(struct.pack('<H', 1))   # NumChannels (Mono)
        f.write(struct.pack('<I', sample_rate)) # SampleRate
        f.write(struct.pack('<I', byte_rate))   # ByteRate
        f.write(struct.pack('<H', block_align)) # BlockAlign
        f.write(struct.pack('<H', 16))  # BitsPerSample

        # data subchunk
        f.write(b'data')
        f.write(struct.pack('<I', data_size))

        # Samples
        for s in samples:
            # Clip to -1.0 to 1.0
            s = max(-1.0, min(1.0, s))
            # Convert to 16-bit PCM
            val = int(s * 32767)
            f.write(struct.pack('<h', val))

def generate_white_noise(duration_sec, sample_rate=44100):
    return [random.uniform(-1.0, 1.0) for _ in range(duration_sec * sample_rate)]

def simple_low_pass(samples, alpha=0.1):
    output = []
    last = 0.0
    for s in samples:
        last = last + alpha * (s - last)
        output.append(last)
    return output

def simple_brown_noise(duration_sec, sample_rate=44100):
    # Brown noise is basically integrated white noise (random walk)
    samples = []
    last = 0.0
    for _ in range(duration_sec * sample_rate):
        white = random.uniform(-1.0, 1.0)
        last = (last + (0.02 * white)) / 1.02 # Leaky integrator to stay in range
        samples.append(last * 3.0) # Boost volume
    return samples

def generate_vinyl_crackle(duration_sec, sample_rate=44100):
    # Mostly silence/low hiss with random pops
    samples = []
    for _ in range(duration_sec * sample_rate):
        base = random.uniform(-0.05, 0.05) # Low hiss
        if random.random() < 0.0005: # Pop
            base += random.uniform(0.5, 0.8)
        samples.append(base)
    return samples

output_dir = 'assets/audio/atmosphere'
os.makedirs(output_dir, exist_ok=True)

print("Generating Rain (Brown Noise)...")
rain_samples = simple_brown_noise(5) # 5 seconds loop
write_wav(os.path.join(output_dir, 'rain_loop.wav'), rain_samples)

print("Generating Wind (Low Passed White Noise)...")
white = generate_white_noise(5)
wind_samples = simple_low_pass(white, alpha=0.05)
# boost
wind_samples = [s * 5.0 for s in wind_samples]
write_wav(os.path.join(output_dir, 'wind_blow.wav'), wind_samples)

print("Generating Tape Hiss (White Noise Low Volume)...")
tape_samples = [s * 0.1 for s in generate_white_noise(5)]
write_wav(os.path.join(output_dir, 'tape_hiss.wav'), tape_samples)

print("Generating Vinyl Crackle...")
vinyl_samples = generate_vinyl_crackle(5)
write_wav(os.path.join(output_dir, 'vinyl_crackle.wav'), vinyl_samples)

print("Done.")
