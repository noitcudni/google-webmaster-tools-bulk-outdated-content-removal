# Google Webmaster Tools Bulk Outdated Content Removal Chrome Extension
```diff
- Upon visiting the Google Webmaster Tools page and submitting the csv file via
- the red trash can, it's possible that nothing happens. If that's the case for you,
- please refresh the page and resubmit your csv file.
```

## Install from Google Webstore
https://chrome.google.com/webstore/detail/webmastertools-bulk-outda/nifcnomokilnniefahjbcelkepelpphl

## Installation
1. Install Java
2. Install [leiningen](http://leiningen.org).
3. Either `git clone git@github.com:noitcudni/google-webmaster-tools-bulk-outdated-content-removal.git` or download the zip file from [github](https://github.com/noitcudni/google-webmaster-tools-bulk-outdated-content-removal/archive/master.zip) and unzip it.
4. `cd` into the project root directory.
  * Run in the terminal
  ```bash
  lein release && lein package
  ```
5. Go to **chrome://extensions/** and turn on Developer mode.
6. Click on **Load unpacked extension . . .** and load the extension. You will find the compiled js files in the `releases` folder.

## Usage
1. Create a list of urls to be removed and store them in a file. See below for format.
2. Go to Google Webmaster Remove Outdated Content page. (https://search.google.com/search-console/remove-outdated-content)
3. Open up the extension popup by clicking on the red trash can icon.
4. Click on the "Submit CSV File" button to upload your csv file. It will start running automatically.

## Local Storage
The extension uses chrome's local storage to keep track of state for each URL. You can use the **Clear cache** button to clear your local storage content to start anew.

~~While the extension is at a paused/stuck state, you can also inspect what URLs are still pending to be removed. Right click anywhere on the page and select **Inspect**. Then, click on the **View local storage** button. The current local storage state will be printed out under the **Console** tab.~~

## CSV Format
* If the content is no longer live
```
url
```

* If a word that's no longer on the live page but in the cached version
```
url, word
```

* If the image url's has been removed or updated
```
image-url, url-of-the-page
```
* It also takes google search image urls
```
https://www.google.com/imgres?imgurl=...
```
