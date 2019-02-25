# Google Webmaster Tools Bulk Outdated Content Removal Chrome Extension
## Install from Google Webstore
https://chrome.google.com/webstore/detail/webmastertools-bulk-outda/nifcnomokilnniefahjbcelkepelpphl

## Installation
1. Download the extension from [github](https://github.com/noitcudni/google-webmaster-tools-bulk-outdated-content-removal/archive/master.zip) and unzip it.
2. Go to **chrome://extension/** and turn on Developer mode.
3. Click on **Load unpacked extension . . .** and load the extension.

## Usage

1. Create a list of outdated urls to be removed and store them in a csv file.
  * First column: url (required)
  * A word that no longer appears on the live page but appears in the cached version (optional)
Example:
```
url0,helloworld
url1
image_url0, url-that-belongs-to
```

If the page no longer exists and the optional word is provided, the corresponding word will be ignored.
On the other hand, if the page still exists, and you failed to provide the optional word, the automation will pause so that you'll have a chance to enter the "missing" word.


2. Visit https://www.google.com/webmasters/tools/removals
3. Click on the "Upload Your File" button.
