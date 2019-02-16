var buttonCss = {
  "background-color": "#d14836",
  "color": "#fff",
  "padding": "8px",
  "line-height": "27px",
  "margin-top" : "4px",
  "margin-left" : "4px"
};


// wait for the first selector to be visible and then click on it
// Then, do the same for the next selector in the array.
function clickWithWait(selectorArray) {
  if (selectorArray.length == 0) return;

  var $target = $(selectorArray[0]);

  if ($target == null || !$target.is(':visible')) {
    window.setTimeout(clickWithWait, 1000, selectorArray);
  } else {
    $target.trigger('click');
    selectorArray.shift();
    // click on the next selector
    clickWithWait(selectorArray);
  }}

// Wait until one of the text shows up
function removal_type_mux() {
  // outdated page removal: "Now you can submit your temporary removal request."
  // change_content: "We think the web page you're trying to remove hasn't been removed by the site owner."

  var $dialog = $("div[role='dialog']");
  console.log($dialog);//xxx

  if ($dialog == null || !$dialog.is(':visible')) {
    window.setTimeout(removal_type_mux, 1000);
  } else {
    if ($dialog.text().includes("analyzingCancel")) {
      window.setTimeout(removal_type_mux, 1000);
    } else if ($dialog.text().includes("Now you can submit your temporary removal request.")) {
      // outdated_page_removal
      clickWithWait(["div[role='dialog'] button div:contains('Request Removal')",
                     "div[role='dialog'] button div:contains('OK')"]);

    } else if ($dialog.text().includes("We think the web page you're trying to remove hasn't been removed by the site owner.")) {
      // changed_content
      clickWithWait(["div[role='dialog'] button div:contains('Next')",
                     "div[role='dialog'] button div:contains('Next')"]);
      // enter your missing word here
      // clickWithWait(["div[role='dialog'] button div:contains('Request Removal')",
      //                "div[role='dialog'] button div:contains('OK')"]);
    }
  }
}

function longPoll() {
  var $requestRemovalbtn = $("button > div:contains('Request Removal')");

  if ($requestRemovalbtn == null || !$requestRemovalbtn.is(':visible')) {
    window.setTimeout(longPoll, 1000);
  } else {
    var port = chrome.runtime.connect({name: "victimPort"});

    port.onMessage.addListener(function(msg) {
      console.log("port.onMessage: ", msg); //xxx
      if (msg.type === 'removeUrl') {
        var victim = msg.victim;
        console.log("about to remove: ", victim);

        $(".gwt-TextBox").attr('value', victim);
        $requestRemovalbtn.trigger('click');

        removal_type_mux();
      }
    });

    var $labelFileInput = $("<label for='fileInput'>Upload Your File</label>");
    var $fileInput = $("<input style='display:none' id='fileInput' type='file' />");
    $labelFileInput.css(buttonCss);

    $requestRemovalbtn.parent().parent().append($labelFileInput);
    $requestRemovalbtn.parent().parent().append($fileInput);

    $fileInput.change(function(){
      $.each(this.files, function(i, f) {
        var reader = new FileReader();
        reader.onload = (function(e) {
          var rawTxt = e.target.result.replace(/\r/g, "\n");
          console.log("rawTxt: " + rawTxt); //xxx
          port.postMessage({
            'type': 'initVictims',
            'rawTxt': rawTxt
          });

        });
        reader.readAsText(f);
      });
    });

    setTimeout(() => {
      port.postMessage({
        'type': 'nextVictim'
      });
    });
  }
}

$(document).ready(() => {
  longPoll();
});

// test word: amphawan
