# Image credits

Concept art generated locally with FLUX.1-dev; not screenshots of the app.

## Tooling

- ComfyUI 0.38.0 (local instance, `127.0.0.1:8188`)
- Model: FLUX.1-dev fp8 (`flux1-dev-fp8.safetensors`, weight dtype `default`)
- Text encoders: `t5xxl_fp8_e4m3fn_scaled.safetensors` + `clip_l.safetensors` (`DualCLIPLoader`, type `flux`)
- VAE: `flux-vae-bf16.safetensors`
- Sampler: `euler` / `simple`, 24 steps, CFG 1.0, denoise 1.0
- Negative prompt: empty (FLUX.1-dev guidance)
- Date: 2026-10-22

## Files

| File | Dimensions | Seed | Generation time |
| --- | --- | --- | --- |
| `hero.png` | 1600x900 | 880705 | 16.83 s |
| `feature-trust.png` | 1024x1024 | 731918 | 22.02 s |
| `feature-isolation.png` | 1024x1024 | 731913 | 22.03 s |
| `feature-warning.png` | 1024x1024 | 731914 | 22.03 s |
| `social-preview.png` | 1280x640 | derived from `hero.png` (seed 880705) | PIL crop + LANCZOS resize |

`hero.png` was generated at 1216x704, upscaled with LANCZOS to 1600x926, then center-cropped to 1600x900. Both labels — **СЕРТИФИКАТЫ** (large, main line) and **ДОВЕРЕННЫЕ СЕРТИФИКАТЫ** (smaller, centered below) — were composited with Python/PIL (DejaVuSans-Bold) using a perspective transform to sit on the box-front panel, with a subtle dark emboss and shadow; the model produced a blank label only. The image contains exactly one matryoshka doll; no second or small doll appears anywhere. Alternate hero seeds 880701, 880702, 880703, 880706 and 880708 were generated and not used (smaller or missing label panel, doll fully inside the box, or pseudo-lettering/logos); seed 880704 was rejected for two extra small dolls and 880707 for gibberish lettering. `social-preview.png` was produced with Python/PIL: `hero.png` center-cropped to 1600x800 (50 px removed top and bottom) and resized to 1280x640 with LANCZOS, keeping the box, the single doll and both full label lines visible.

## Prompts

### hero.png

Premium cinematic product photograph of a dark navy and charcoal cardboard technical product box, the box is open at the top with tall side flaps, and EXACTLY ONE matryoshka doll only, one single doll, one large glossy lacquered matryoshka head and upper body emerging from the open box, no second doll, no small doll, no doll beside the box, no doll on the floor, no doll in the background, the single large matryoshka has warm amber and gold painted folk patterns, three-quarter front view, the product box front faces the camera straight on at a slight angle, a large clean BLANK wide rectangular label panel spanning most of the front face of the box, NO text, NO letters, NO writing, NO logos, NO symbols on the box or label, subtle constructivist diagonal geometry in the background, thin amber circuit accents behind the box, deep navy charcoal studio background, volumetric haze, warm amber-gold rim light, reflective dark glossy floor, high-end security product key art, octane render, ultra detailed, sharp focus, textless, wordless, no flags, no government buildings, no military symbols, no coat of arms, no country outline, no Mozilla logo.

The model output contained no readable lettering; **СЕРТИФИКАТЫ** and **ДОВЕРЕННЫЕ СЕРТИФИКАТЫ** were added afterwards with Python/PIL as perspective-warped, warm off-white label lines with a subtle dark emboss and shadow. Dimensions: generated 1216x704, final 1600x900.

### feature-trust.png

Concept illustration of browser certificate trust, single seamless scene. On the left, a large golden circular trust seal of smooth concentric polished rings with a clear stylized matryoshka nesting doll silhouette at its core. Three thin glowing amber light threads stretch from the seal across to a sleek dark browser address bar floating on the right, with a small amber padlock at its right end. The threads visibly connect the seal to the address bar. Deep navy-charcoal seamless gradient background with no panels or seams, subtle constructivist diagonal accents, floating dust particles, volumetric glow, polished gold and amber, cinematic rim light, high-end 3D render, sharp focus, textless, no lettering, no words, no numbers, no logos, no flags.

### feature-isolation.png

Premium 3D concept art of browser storage isolation. Two clearly separated translucent glass vault chambers side by side, equal size, the left chamber lit cool blue and neutral silver, the right chamber lit warm amber and gold. Inside each chamber, floating abstract cookie tokens, storage blocks and data cubes are visible, neatly contained. Between the two chambers stands a glowing vertical partition wall of fine geometric lattice and light, separating them completely. Dark navy charcoal background, reflective floor, volumetric light shafts, constructivist accents, high-end technical product render, ultra detailed, sharp focus, textless, no lettering, no words, no numbers, no logos, no flags, no symbols.

### feature-warning.png

Premium security concept art. A large amber warning shield hovers above a dark minimal browser toolbar at the bottom of the frame, the shield glowing from within, with a subtle exclamation mark shape cut out of it as clean negative space. Behind the shield float a few translucent geometric certificate-like rings and angular seal patterns in faint gold. Dramatic amber rim light, dark navy and charcoal atmosphere, volumetric haze, reflective surfaces, cinematic high-end 3D render, ultra detailed, sharp focus, textless, no lettering, no words, no numbers, no logos, no flags, no political symbols.

## social-preview.png

Derived from `hero.png` with Python/PIL; no additional model generation.
