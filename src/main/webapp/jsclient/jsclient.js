var rapla = {
  RaplaCallback : function() {
   this.callback = function(api)
      {
        var facade = api.getFacade();
        var resources = facade.getAllocatables();
        var resource = resources[0];
        console.log( resource.getName(null));
      }
  }
}

