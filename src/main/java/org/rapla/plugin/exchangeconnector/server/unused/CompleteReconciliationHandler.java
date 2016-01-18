/**
 *
 */
package org.rapla.plugin.exchangeconnector.server.unused;


/**
 * @author lutz
 */
//public class CompleteReconciliationHandler  {
//
//
//    /**
//     * @param clientFacade
//     */
//    public CompleteReconciliationHandler(RaplaFacade clientFacade) {
//      //  super(null,clientFacade);
//    }
//
//    public void run() {
//     /*   deleteExchangeItemsFromRapla(clientFacade);
//        ExchangeAppointmentStorage.getInstance().setAllDeleted();
//        try {
//            deleteAll();
//            uploadAll();
//            downloadAll();
//        } catch (Exception e) {
//            SynchronisationManager.logException(e);
//        }*/
//    }
//
//    /**
//     * @throws InterruptedException
//     */
//    private void downloadAll() throws InterruptedException {
//       /* Thread thread = new Thread(new ScheduledDownloadHandler(clientFacade));
//        thread.start();
//        while (thread.isAlive()) {
//            this.wait(100);
//        }*/
//    }
//
//    /**
//     * @throws RaplaException
//     * @throws Exception
//     */
//    private void uploadAll() throws RaplaException, Exception {
//
//       /* DynamicType importEventType = ExchangeConnectorPlugin.getImportEventType(clientFacade);
//        final Date from = ExchangeConnectorPlugin.getSynchingPeriodPast(new Date());
//        final Date to = ExchangeConnectorPlugin.getSynchingPeriodFuture(new Date());
//
//        for (User user : clientFacade.getUsers()) {
////			User user= clientFacade.getUser(raplaUsername);
//
//
//            Reservation[] reservations = clientFacade.getReservations(user,
//                    from, to, null);
//            for (Reservation reservation : reservations) {
//                // only upload those which weren't added by Exchange
//                if (importEventType != null && !reservation.getClassification().getType().getElementKey().equals(importEventType.getElementKey())) {
//                    for (Appointment appointment : reservation.getAppointments()) {
//                        AddUpdateWorker worker = new AddUpdateWorker(null, clientFacade, appointment, null);
//                        worker.perform();
//                    }
//                }
//            }
//        }*/
//    }
//
//    /**
//     * @throws Exception
//     */
//    private void deleteAll() throws Exception {
//       for (Appointment appointment : ExchangeAppointmentStorage.getInstance().getDeletedItems()) {
//            String raplaUsername = ExchangeAppointmentStorage.getInstance().getRaplaUsername(appointment);
//            DeleteWorker worker = new DeleteWorker(null, clientFacade, raplaUsername,
//                    ExchangeConnectorUtils.getAppointmentSID(appointment));
//            worker.perform();
//        }
//        ExchangeAppointmentStorage.getInstance().clearStorage();
//    }
//}
