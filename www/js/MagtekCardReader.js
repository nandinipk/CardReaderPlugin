

window.MagtekCardReader = (function () {
    function Dome (els) {
         
    }
     
    var cardReader = {
        openDevice : function () {
          console.log('dsadasdsadsa')
          window.cordova.plugins.MagTek.openDevice(function(device) {
            console.log('Open device Success');
          }, function(error) {
              console.log('Open device Error');
          });
        },
        startScan: function() {
          window.cordova.plugins.MagTek.openDevice(function(device) {
            window.cordova.plugins.MagTek.listenForEvents(function(card_data) {

            console.log('Data from reader', JSON.stringify(card_data));

            var track2Data = ''
            var trackData = card_data['Card.DecryptedTrack2'];

            if (trackData && trackData.split('=')[0]) {
                track2Data = trackData.split('=')[0];
                track2Data = track2Data.substr(1);
            }

            var cardData = {};
            cardData.cardHolderName = card_data['Card.Name'];
            cardData.cardExpiry = card_data['Card.ExpDate'];
            cardData.cardNumber = track2Data;

            console.log('Card Data', JSON.stringify(cardData));

          }, ['TRANS_EVENT_ERROR', 'TRANS_EVENT_OK', 'TRANS_EVENT_START'], function(error) {
              console.log('Error occurred while listening to events')
          });
              console.log('Open device Success');
          }, function(error) {
              console.log('Open device Error');
          });
        },
        closeDevice: function() {
          window.cordova.plugins.MagTek.closeDevice(function(succes){
            console.log('Close device Success');
          }, function(error) {
            console.log('Close device Error');
          });
        }
    };
     
    return cardReader;
}());

