var rapla = {
  RaplaCallback : function() {
   this.callback = function(api)
      {
        var facade = api.getFacade();
        var resources = facade.getAllocatables();
        var resource = resources[0];
        console.log( "Starting js-demo");
        console.log( resource.getName(null));
        var calendar = api.getCalendarModel();
        calendar.load(null);
        console.log( "Calendar Model loaded: " + calendar);
        var timeIntervall =calendar.getTimeIntervall();
        var printer = new org.rapla.client.gwt.GwtActionWrapper();
        printer.setRunnable(()=> console.log("Hallo blocks"));
        console.log( "TimeIntervall " + printer);
        var blocksPromise = calendar.queryBlocks( timeIntervall);
        blocksPromise.thenRun(printer);
        console.log( "Blocks ");
      }
  }
}


