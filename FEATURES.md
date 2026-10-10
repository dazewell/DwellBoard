# DwellBoard features

Extra bits I've added on top of [HeliBoard](https://github.com/HeliBorg/HeliBoard). Where something has a setting, I've said so.

<!-- House style: an entry is a definition, not a writeup. Max 70 words of prose
     under the ### heading (images and tables don't count); most sit around 35.
     What it does and where it is, then its setting and default. No edge cases,
     no failure behaviour, no rationale. Extending a feature means folding it
     into the existing entry and re-counting, never a second heading.
     Every feature carries its commit tag as a marker: ### Name <!-- #slug -->
     process-rules.yml fails a PR whose feature #slug has no marker here. -->

### Don't learn capitalized words <!-- #no-learn-capitalized -->

Keeps words with a single capital letter, like "Hello", out of learned words; "HELlo" is still learned. Words at the start of a sentence are unaffected. Setting: Text correction → "Don't learn capitalized words", off by default.

### Instant space after punctuation <!-- #punctuation-autospace -->

With "Autospace after punctuation" on, typing , ; : ! ? after a word, or a dash after "word ", adds the space right away; a space you type yourself is not doubled. Setting: Text correction → "Autospace after punctuation", off by default.

### DwellBoard name <!-- #dwellboard-name -->

The app, its spell checker and its settings screen are labelled "DwellBoard", and built APKs are named `DwellBoard_<version>-<type>.apk`. The application id is unchanged, so it installs alongside HeliBoard and keeps existing data.
