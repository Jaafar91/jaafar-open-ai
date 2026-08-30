# Journey test plan

This checklist is for every release APK. It separates checks verified by reading the code from checks that require a real Android device and a real document.

## Audit result

| ID | Journey | Result | Notes |
| --- | --- | --- | --- |
| FONT-03 | Complete a selected character set | Fixed in this PR | Completion previously counted all saved drawings, including optional characters. It now checks every required character. |
| DOC-01 | Add a text mark | Pass â€” code checked | The flow is Text â†’ tap location â†’ type â†’ Add text. |
| DOC-02 | Select, edit, move, and delete a mark | Pass â€” code checked | Selected marks expose Edit and Delete; dragging updates position. |
| NAV-01 | Home navigation | Pass â€” code checked | Home routes to documents, images, fonts, library, restyle, and settings. |
| DEVICE-01 to DEVICE-10 | Files, sharing, rendering, and updates | Manual device test required | These depend on Android storage, installed apps, and real PDF/image files. |

## 1. Home and navigation

| ID | Steps | Expected result | Status |
| --- | --- | --- | --- |
| NAV-01 | Open the app. | Home title is **Font Maker**. | Code checked |
| NAV-02 | Open each home card, then use Back. | Page opens and Back returns to Home without losing the app state. | Manual |
| NAV-03 | Open Settings, switch dark mode, close and reopen the app. | Theme choice remains selected. | Manual |
| NAV-04 | Open My Library with no saved assets. | Clear empty-state information is shown; no crash. | Code checked |

## 2. Create a font

| ID | Steps | Expected result | Status |
| --- | --- | --- | --- |
| FONT-01 | Fonts â†’ Create a handwriting font â†’ name it â†’ choose letters. | New project opens the drawing journey. | Manual |
| FONT-02 | Draw a character, Save & Next, then return to Fonts. | Progress increases by one required character. | Code checked |
| FONT-03 | Draw required letters plus extra phrase letters. | The font is complete only when every selected character has been drawn. | Fixed / code checked |
| FONT-04 | Finish all selected letters. | Completion screen says the font is ready; it does not offer duplicate continuation actions. | Manual |
| FONT-05 | In a drawing screen, compare the reference card and canvas. | Large reference is visible above the canvas and faint guide remains inside it. | Manual |
| FONT-06 | Undo, Clear, Skip, Back with unsaved drawing. | Each action affects only the current character; Back asks before discarding strokes. | Manual |
| FONT-07 | Generate a font and type sample text. | Preview renders using the generated font; export shares a TTF file. | Manual |

## 3. Import and manage fonts

| ID | Steps | Expected result | Status |
| --- | --- | --- | --- |
| LIB-01 | Import a valid .ttf or .otf. | Font is named and appears in My Library. | Manual |
| LIB-02 | Choose an imported font for image editing. | The selected font is applied in the image editor. | Manual |
| LIB-03 | Try an invalid or unreadable file. | Friendly error; app stays usable. | Manual |
| LIB-04 | Delete an imported font or saved asset. | Only the selected asset is removed. | Manual |

## 4. Signatures and stamps

| ID | Steps | Expected result | Status |
| --- | --- | --- | --- |
| SIGN-01 | Create a hand-drawn signature, save it, then reopen the screen. | Saved signature preview and name appear. | Manual |
| SIGN-02 | Create/import a stamp. | It appears as a stamp, separate from signatures. | Manual |
| SIGN-03 | Open Complete a document without saved signatures/stamps. | Signature and Stamp tools are hidden. | Code checked |
| SIGN-04 | Save a signature or stamp, then open Complete a document. | Correct tool becomes available and shows a visible preview when selected. | Manual |

## 5. Complete a document

| ID | Steps | Expected result | Status |
| --- | --- | --- | --- |
| DOC-01 | Choose a PDF or image â†’ Text â†’ tap a location â†’ type text â†’ Add text. | Text appears at the selected location. | Code checked |
| DOC-02 | Select added text. Edit its content, drag it, then Delete it. | Each change is visible immediately; Delete removes the mark. | Code checked |
| DOC-03 | Choose Quick â†’ Approved/Paid/Received/Confidential â†’ tap a location. | Preset is placed at that location. | Code checked |
| DOC-04 | Add Date and Check marks. | Each mark uses the selected style and color. | Manual |
| DOC-05 | Add a signature or stamp. | A visible saved asset is placed, can be moved, edited, and deleted. | Manual |
| DOC-06 | Use a multi-page PDF. Add a mark to one page, then choose All pages. | Mark appears only on selected page first, then on every page after All pages. | Manual |
| DOC-07 | Export PDF and image. | Shared output contains every visible mark at the expected position. | Manual |
| DOC-08 | Cancel document selection. | User remains on Complete a document; no crash. | Code checked |

## 6. Edit an image

| ID | Steps | Expected result | Status |
| --- | --- | --- | --- |
| IMG-01 | Choose a photo and a font. | Image editor opens with the chosen font. | Manual |
| IMG-02 | Add text, signature, and stamp; move each. | Canvas preview matches exported image. | Manual |
| IMG-03 | Cancel/share/save repeatedly. | No duplicate output or crash. | Manual |

## 7. Restyle scanned text

| ID | Steps | Expected result | Status |
| --- | --- | --- | --- |
| OCR-01 | Choose a clear single-page scanned PDF and a font. | Recognized text is restyled and exported. | Manual |
| OCR-02 | Choose a multi-page scan. | Every page is processed or a clear limitation message is shown. | Manual |
| OCR-03 | Choose a scan with no readable text. | Friendly error; original document is not damaged. | Manual |
| OCR-04 | Use very small source text. | No empty-range crash; outcome is either an output or a readable error. | Manual |

## 8. Release and update

| ID | Steps | Expected result | Status |
| --- | --- | --- | --- |
| REL-01 | Open the GitHub Actions run for the PR. | Debug/release APK artifact is available after a successful build. | Manual |
| REL-02 | Install two successive signed release APKs. | Second APK updates the first without a package conflict. | Manual |
| REL-03 | Install the APK on Android 10, 12, and current Android where available. | File picker, share sheet, and rendering work. | Manual |

## Failed test cases fixed

1. **FONT-03: False font completion.** Optional or phrase-based drawings could raise the drawing count above the required total while one selected character was missing. The completion screen would then claim the font was ready. This release checks the actual required character set instead of comparing counts.

## How to run this checklist

1. Download the PR APK from GitHub Actions.
2. Run every Manual item on a physical phone using a PDF, an image, a valid TTF/OTF, and one invalid file.
3. Record the test ID and attach a screenshot for any failure.
4. Do not publish a release until all required manual tests pass.

