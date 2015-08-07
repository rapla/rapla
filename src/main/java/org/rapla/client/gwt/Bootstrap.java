package org.rapla.client.gwt;

import java.util.Arrays;
import java.util.Collection;

import javax.inject.Inject;
import javax.inject.Provider;

import org.rapla.client.Application;
import org.rapla.entities.domain.Allocatable;
import org.rapla.facade.ClientFacade;
import org.rapla.facade.internal.FacadeImpl;
import org.rapla.framework.RaplaException;
import org.rapla.framework.logger.Logger;
import org.rapla.rest.gwtjsonrpc.common.AsyncCallback;
import org.rapla.rest.gwtjsonrpc.common.FutureResult;
import org.rapla.rest.gwtjsonrpc.common.VoidResult;
import org.rapla.storage.RaplaSecurityException;

import com.google.gwt.user.client.Window;


public class Bootstrap {

   @Inject private Provider<Application> application;
   @Inject private ClientFacade facade;
   @Inject private Logger logger;
   
   public void load(){
       FacadeImpl facadeImpl = (FacadeImpl) facade;
       facadeImpl.setCachingEnabled( false );
       FutureResult<VoidResult> load = facadeImpl.load();
       logger.info("Loading resources");
       load.get( new AsyncCallback<VoidResult>() {
           
           @Override
           public void onSuccess(VoidResult result) {
              try {
                  Collection<Allocatable> allocatables = Arrays.asList(facade.getAllocatables());
                  logger.info("loaded " + allocatables.size() + " resources. Starting application");
                  application.get().start();
              } catch (RaplaException e) {
                  onFailure(e);
              }
           }
           
           @Override
           public void onFailure(Throwable e) {
               logger.error(e.getMessage(), e);
               if ( e instanceof RaplaSecurityException)
               {
                   Window.Location.replace("../rapla?page=auth");
               }
           }
       });

   }
}
