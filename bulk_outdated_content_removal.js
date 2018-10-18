function longPoll() {
  var $requestRemovalbtn = $("#gwt-uid-28");

  if ($requestRemovalbtn == null || !$requestRemovalbtn.is(':visible')) {
    window.setTimeout(longPoll, 1000);
  } else {
    // set up stuff
    console.log("Yay..ready!"); //xx
    // $requestRemovalbtn.append("WTF");
    // $("#gwt-uid-28").append("WTF");
    $requestRemovalbtn.parent().parent().append("WTFWTF!!!");
  }
}

$(document).ready(() => {
  longPoll();

  // console.log("here"); //xxx
  // var $fileInput = $("<input id='fileInput' type='file' />");
  // console.log($("#gwt-uid-28")); //xxx
  // $("#gwt-uid-28").append("WTF");
  // $("#gwt-uid-28").parent().parent().append($fileInput);

  // $fileInput.change(() => {
  // });

});
