# Real audio sources

Use only real human/lexicon audio. Do not use browser speechSynthesis or TTS.

Preferred open sources:

1. Wikimedia Commons / Lingua Libre
   - German word pronunciation audio.
   - Many files are OGG/WAV and can be converted to MP3 for Android/Web assets.

2. Shtooka / SWAC collections
   - Human word and phrase recordings.
   - Can be imported into `app/src/main/assets/audio/`.

3. Mozilla Common Voice
   - Real sentence recordings under CC0.
   - Useful only when the sentence text matches a card example exactly.

Expected local filenames:

```text
app/src/main/assets/audio/DE0001_word.mp3
app/src/main/assets/audio/DE0001_spell.mp3
app/src/main/assets/audio/DE0001_cn.mp3
app/src/main/assets/audio/DE0001_ex.mp3
app/src/main/assets/audio/DE0001_excn.mp3
```

The app plays these local files first. Missing files are skipped. No TTS fallback.
