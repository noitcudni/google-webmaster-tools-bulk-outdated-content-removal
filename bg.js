var executionInProgress = false;
var victimUrlArray = null;

chrome.runtime.onConnect.addListener((port) => {
  port.onMessage.addListener((msg) => {
    if (msg.type === 'initVictims') {
      executionInProgress = true;
      victimUrlArray = msg.rawTxt.replace(/^\s+|\s+$/g, '').split('\n');
      victimUrlArray = victimUrlArray.filter(v => v.length > 0);

      var victimUrl = victimUrlArray[0];
      console.log("victimUrlArray: " + victimUrlArray);//xxx

      port.postMessage({
        'type' : 'removeUrl',
        'victim' : victimUrl
      });

    } else if (msg.type === 'nextVictim') {
      victimUrlArray.shift();
      var victimUrl = victimUrlArray[0];

      if (victimUrl !== undefined) {
        port.postMessage({
          'type' : 'removeUrl',
          'victim' : victimUrl
        });
      } else {
        executionInProgress = false; //done
      }

      // find the next victim
      if (executionInProgress) {

      } else {
        console.log("no victim to be executed.");
      }
    }
  });

});
