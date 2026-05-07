var apiCallback = (api) =>
        {
          var errorFunction = (message) =>  api.error("Error: " + message);
          var facade = api.getFacade();
          var resources = facade.getAllocatables();
          var resource = resources[0];
          var raplaLocalce = api.getRaplaLocale();
          api.info( "Starting js-demo");
          api.info( resource.getName(null));
          var calendar = api.getCalendarModel();
          calendar.load(null);
          api.info( "Calendar Model loaded: " + calendar);
          var timeIntervall =calendar.getTimeIntervall();
          api.loadTableModel(calendar).thenAccept( (table)=>
              {
                 if (table.getRowCount() > 0)
                 {
                    api.info("TableRow: " + table.getValueAt(0,0));
                 }
              }
           ).exceptionally( errorFunction);
          var blocksPromise = calendar.queryBlocks( timeIntervall);
          blocksPromise.thenRun(()=> api.info("Hallo blocks")).exceptionally(errorFunction);
          //blocksPromise.thenRun(()=> console.log("Hallo blocks")).exceptionally(new rapla.Error(errorFunction).get());
          createReservation( api,resource);
        };



var createReservation = (api,resource) =>
{
          var errorFunction = (message) =>  api.error("Error: " + message);
          var facade = api.getFacade();
          api.info( "New Reservation ");/**/
          var eventType = facade.getDynamicTypes("reservation")[0];
          var classification = eventType.newClassification();
          var reservationController = api.getReservationController();
          classification.setValue("name","Test");
          facade.newReservationAsync(classification).thenCompose( newReservation =>
          {
              var dateParse = rapla.DateParse.INSTANCE;
              var startDate = dateParse.parseDateTime("2018-01-03","12:00");
              var endDate = dateParse.parseDateTime("2018-01-03","14:00");
              api.info( "New Appointment: " + dateParse.formatTimestamp( startDate) + " - " + dateParse.formatTimestamp( endDate));
              var timeIntervall = new rapla.TimeInterval( startDate, endDate );
              return facade.newAppointmentAsync(timeIntervall).thenApply( appointment =>
              {
                  newReservation.addAppointment( appointment);
                  newReservation.addAllocatable(resource);
                  api.info( "Saving Reservation ");
                  // Next Method will cause an error so it will prevent Reservation from saving. And test the error Method;"
                  facade.getPersistant( newReservation);
                  return newReservation;
              });
           })
           .thenCompose((reservationWithAppointment)=>
             reservationController.saveReservation(null, reservationWithAppointment)
           )
           .thenRun(()=>api.info("Reservation saved"))
           .exceptionally(errorFunction);

};


var rapla = {
  RaplaCallback : function() {
   this.gwtLoaded = (starter) =>
      {
         var errorFunction = (info) => console.error(info);
         var registerAction = ()=> {
                             var loginToken = starter.getValidToken();
                             if ( loginToken != null)
                             {
                                var accessToken = loginToken.getAccessToken()
                                console.log( "AccessToken " + accessToken);
                                starter.registerApi( accessToken).thenAccept( apiCallback );
                             }
                          }
          starter.initLocale("de_DE").thenRun (registerAction).exceptionally( errorFunction);
      };
  }
}


