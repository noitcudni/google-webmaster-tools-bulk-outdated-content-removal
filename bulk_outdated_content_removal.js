var buttonCss = {
  "background-color": "#d14836",
  "color": "#fff",
  "padding": "8px",
  "line-height": "27px",
  "margin-top" : "4px",
  "margin-left" : "4px"
};

function longPoll() {
  var $requestRemovalbtn = $("#gwt-uid-28");

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

        setTimeout(() => {
          // modal request removal btn
          $("#gwt-uid-88").trigger('click');
          setTimeout(() => {
            // click on ok
            $("#gwt-uid-96").trigger('click');
          }, 1300);
        }, 1300);
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
