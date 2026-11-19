# AI Benchmark Tool

Benchmark tool to evaluate the speed and accuracy of AI models (Gemini and Groq) across Kharcha Paani's three AI workflows:
- Bill extraction from sample receipts and invoices
- Voice transcription and extraction from synthetic audio samples
- Text extraction from natural language expense entries

## Usage

1. Generate test assets (run once):
   ```bash
   python tools/ai-bench/make_assets.py
   ```

2. List available models based on configured keys:
   ```bash
   python tools/ai-bench/bench.py --list
   ```

3. Run benchmarks:
   ```bash
   python tools/ai-bench/bench.py
   ```

API keys are read from `%USERPROFILE%/.kharcha-paani/ai-keys.env`.

## Results

Benchmark outputs (raw JSON data and summary Markdown reports) are written to `results/`.
This directory is gitignored so test runs do not pollute the repository.
