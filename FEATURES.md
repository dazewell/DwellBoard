# DwellBoard features

Extra bits I've added on top of [HeliBoard](https://github.com/HeliBorg/HeliBoard). Where something has a setting, I've said so.

<!-- House style: an entry is a definition, not a writeup. Max 70 words of prose
     under the ### heading (images and tables don't count); most sit around 35.
     What it does and where it is, then its setting and default. No edge cases,
     no failure behaviour, no rationale. Extending a feature means folding it
     into the existing entry and re-counting, never a second heading.
     Every feature carries its commit tag as a marker: ### Name <!-- #slug -->
     process-rules.yml fails a PR whose feature #slug has no marker here. -->

### Learn capitalized words in lowercase <!-- #no-learn-capitalized -->

Learns capitalized ("Hello") and ALL CAPS ("HELLO") words in lowercase; mixed case like "HELlo" is learned as typed. A capitalized word at the start of a sentence is unaffected. Setting: Text correction → "Learn capitalized words in lowercase", off by default.

### Learning contexts <!-- #learning-contexts -->

Keeps learned words, predictions and recent emojis separate for chosen sets of apps. Create contexts under Learning contexts in settings, optionally copying another's learned data, and pick their apps, enabled ones listed first; other apps use the default context. Clear learned data from a menu (Default's is in its learned words), or delete a context. A toolbar arrow dot shows when the app uses a context; long-press for its name.

### Learned words <!-- #learned-words -->

Shows the words a learning context has learned that are not in the dictionary (the menu adds the rest), grouped by language, alphabetically or most frequent first. Search, tick the unwanted ones and delete them along with their predictions. Open it from a context's menu, or tap Default under Learning contexts.

### Learn new words only when tapped <!-- #learn-only-picked -->

Words that are in no dictionary are learned only when you tap them in the suggestion strip, not when you type a space after them; the strip then shows the word you are typing. Known words still learn as you type. Setting: Text correction → "Learn new words only when tapped", off by default.

### Instant space after punctuation <!-- #punctuation-autospace --> <!-- #punctuation-space-swap -->

With "Autospace after punctuation" on, typing , ; : ! ? after a word, ; ! ? after a number, or a dash after "word ", adds the space right away; a space you type yourself is not doubled. The same marks typed after "word ", also with the cursor before the next word, move in front of that space: "word ," becomes "word, ". Setting: Text correction → "Autospace after punctuation", off by default.

### DwellBoard name <!-- #dwellboard-name -->

The app, its spell checker and its settings screen are labelled "DwellBoard", and built APKs are named `DwellBoard_<version>-<type>.apk`. Its application id is `helium314.keyboard.dazewell`, so it installs alongside HeliBoard.

### Recent emoji count <!-- #recent-emoji-count -->

Sets how many recent emojis the emoji keyboard shows, up to three times the usual number, and how many columns each page has, from 4 to 12; more columns shrink emojis and row spacing to fit. Settings: Appearance → "Recent emojis shown" (80 by default) and "Emoji columns" (Auto).

### Gap above space bar row <!-- #space-row-gap -->

Adds empty space above the space bar row on the letter and symbol keyboards, up to one row's height where the keys are wide enough for taps in it to still register; the rows above shrink so the keyboard stays the same height. Setting: Appearance → "Gap above space bar row", per orientation, 0% by default.

### Pill keyboard style <!-- #pill-style -->

A Gboard-like style: flat keys with no bottom edge, roomier gaps in portrait, pill-shaped ?123 and action keys, and the globe key coloured like a letter. Looks best with key borders on; the key gap setting still applies. Setting: Appearance → Style → "Pill"; Material stays the default.

### Clear emoji search <!-- #emoji-search-clear -->

Emoji search starts empty again once you've returned to the main keyboard yourself with ABC or the emoji key; searching and picking emojis in between keeps what you typed. Setting: Preferences → "Clear emoji search", on by default.

### Emoji names from any language <!-- #emoji-name-fallback -->

Long-pressing an emoji shows its name even when the current language's emoji dictionary lacks one: the emoji dictionaries of your other enabled languages are tried next, and a skin-tone variant falls back to its base emoji's name. Setting: Preferences → "Show emoji description on long press", on by default; needs an emoji dictionary.

### Main keyboard after emoji and space <!-- #emoji-space-abc -->

Pressing space or enter on the emoji keyboard after picking an emoji returns to the main keyboard, like the existing symbols and numpad options; emoji search is not affected. Setting: Advanced → "Switch to main keyboard after… Pressing enter or space after other keys in emoji view", off by default.

### Main keyboard after punctuation <!-- #symbols-punctuation-abc -->

Typing one of a chosen set of punctuation marks on the symbols keyboard returns to the main keyboard, so a "?" or "-" in a sentence needs no extra tap. Setting: Advanced → "Switch to main keyboard after… Typing certain punctuation in symbols view", off by default; the marks are editable below it, default ? ! ; - " ' ).

### Gboard style themes <!-- #dynamic-swapped-theme --> <!-- #gboard-themes -->

Four color themes matching Gboard's current Dynamic Color look: "Gboard style" light and dark, with keys lighter than a tinted background and one shade for all function keys and Enter, plus "Gboard style swapped" versions with background and key colors exchanged. Pick them under Appearance → Colors; Android 12 and newer. Keys only show their color with key borders on.

### Enter swipe <!-- #enter-swipe -->

Like delete swipe, but on the enter key and nothing is deleted: hold enter and slide left to select letters or words before the cursor, then release to keep the selection for cutting, copying or typing over. Sliding up, down or right off the key still cancels enter. Setting: Advanced → "Enter swipe" (Off, Letters, Words), off by default.
