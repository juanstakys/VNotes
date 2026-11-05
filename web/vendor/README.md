# Pinned browser dependencies

Downloaded from jsDelivr's distribution of `@huggingface/transformers@3.7.1`:

- transformers.min.js
- ort-wasm-simd-threaded.jsep.mjs
- ort-wasm-simd-threaded.jsep.wasm

Source pattern: https://cdn.jsdelivr.net/npm/@huggingface/transformers@3.7.1/dist/FILE

The included ONNX Runtime is version 1.21.0. Licenses are included in TRANSFORMERS-LICENSE (Apache 2.0) and ONNX-RUNTIME-LICENSE (MIT). The bundled runtime retains its embedded third-party license comments. Model weights are MIT-licensed and downloaded from onnx-community/moonshine-tiny-ONNX at revision a6da124 during explicit setup.

No package manager runs during installation or use. Serving local runtime files avoids CDN import resolution and CORS issues; service worker caching makes them available offline after setup. Runtime files add about 22 MB to the source package. Do not edit generated vendor code.

Verify downloaded bytes with `sha256sum -c vendor/SHA256SUMS` from the project root. These hashes record the reviewed downloaded files; they are not an independent vendor signature. When upgrading, replace all three files together, update hashes, test warmup and actual speech, and bump the voice cache version.
