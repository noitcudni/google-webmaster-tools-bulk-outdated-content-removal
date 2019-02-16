var executionInProgress = false;
var rowArray = null;

chrome.runtime.onConnect.addListener((port) => {
  port.onMessage.addListener((msg) => {
    if (msg.type === 'initVictims') {
      executionInProgress = true;
      rowArray = msg.rawTxt.replace(/^\s+|\s+$/g, '').split('\n');
      rowArray = rowArray.filter(v => v.length > 0);

      var rowTxt = rowArray[0];
      console.log("rowArray: " + rowArray);//xxx

      port.postMessage({
        'type' : 'removeUrl',
        'rowTxt' : rowTxt
      });

    } else if (msg.type === 'nextVictim') {
      // find the next victim
      if (executionInProgress) {
        rowArray.shift();
        var rowTxt = rowArray[0];

        if (rowTxt !== undefined) {
          port.postMessage({
            'type' : 'removeUrl',
            'rowTxt' : rowTxt
          });
        } else {
          executionInProgress = false; //done
          rowArray = null;
        }
      } else {
        console.log("no victim to be executed.");
      }

      // find the next victim
      if (executionInProgress) {

      } else {
        console.log("no victim to be executed.");
      }
    }
  });

});
