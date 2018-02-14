var apiCallback = (api) =>
        {
          var errorFunction = new rapla.Catch((info) => console.log(info));
          var facade = api.getFacade();
          var resources = facade.getAllocatables();
          var resource = resources[0];
          var raplaLocalce = api.getRaplaLocale();
          console.log( "Starting js-demo");
          console.log( resource.getName(null));
          var calendar = api.getCalendarModel();
          calendar.load(null);
          console.log( "Calendar Model loaded: " + calendar);
          var timeIntervall =calendar.getTimeIntervall();
          var blocksPromise = calendar.queryBlocks( timeIntervall);
          blocksPromise.thenRun(new rapla.Action(()=> console.log("Hallo blocks")));
          console.log( "New Reservation ");/**/
          var eventType = facade.getDynamicTypes("reservation")[0];
          var classification = eventType.newClassification();
          var newReservation = facade.newReservation(classification, api.getUser());
          classification.setValue("name","Test");
          var dateParse = rapla.DateParse.INSTANCE;
          var startDate = dateParse.parseDateTime("2018-01-03","12:00");
          var endDate = dateParse.parseDateTime("2018-01-03","14:00");
          console.log( "New Appointment: " + dateParse.formatTimestamp( startDate) + " - " + dateParse.formatTimestamp( endDate));
          newReservation.addAppointment(facade.newAppointment(startDate, endDate));
          newReservation.addAllocatable(resource);
          console.log( "Saving Reservation ");/**/
          api.getReservationController().saveReservation(null, newReservation).thenRun(new rapla.Action(()=>console.log("Reservation saved"))).exceptionally(errorFunction);
        };

var rapla = {
  RaplaCallback : function() {
   this.gwtLoaded = (starter) =>
      {
         var errorFunction = new rapla.Catch((info) => console.log(info));
         var registerAction = ()=> {
                    var loginToken = starter.getValidToken();
                    if ( loginToken != null)
                    {
                       var accessToken = loginToken.getAccessToken()
                       console.log( "AccessToken " + accessToken);
                       starter.registerApi( accessToken).thenAccept(new rapla.Consumer( apiCallback));
                    }
                 };

          starter.initLocale("de_DE").thenRun (new rapla.Action(registerAction)).exceptionally( errorFunction);
      };
  }
}


