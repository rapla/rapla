/**
 * 
 */
package org.rapla.plugin.exchangeconnector.server.unused;



/**
 * @author lutz
 *
 */
//public class ScheduledDownloadHandler extends TimerTask {
//
//    private final RaplaContext context;
//    private final RaplaFacade clientFacade;
//    private final Logger logger;
//
//    /**
//	 * @param clientFacade
//	 */
//	public ScheduledDownloadHandler(RaplaContext context, RaplaFacade clientFacade, Logger logger) {
//		super();
//        this.context = context;
//        this.clientFacade = clientFacade;
//        this.logger = logger;
//
//	}
//
//	/* (non-Javadoc)
//	 * @see java.util.TimerTask#run()
//	 */
//	@Override
//	public void run() {
//		try {
//			deleteExchangeItemsFromRapla();
//			downloadExchangeAppointments();
//		} catch (Exception e) {
//            logger.error(e.getMessage(), e);
//
//		}
//	}
//
//    private synchronized void deleteExchangeItemsFromRapla() {
//        HashSet<Reservation> reservations = new HashSet<Reservation>();
//        for (Appointment appointment : ExchangeAppointmentStorage.getInstance().getExchangeItems()) {
//            reservations.add(appointment.getReservation());
//        }
//        for (Reservation reservation : reservations) {
//            try {
//                clientFacade.remove(reservation);
//            } catch (RaplaException e) {
//
//            }
//        }
//        ExchangeAppointmentStorage.getInstance().removeExchangeItems();
//        ExchangeAppointmentStorage.getInstance().save();
//    }
//
//
//    private void downloadExchangeAppointments() throws Exception {
//		for (String raplaUsername: ExchangeAccountInformationStorage.getInstance().getAllRaplaUsernames()) {
//			if (ExchangeAccountInformationStorage.getInstance().isDownloadFromExchange(raplaUsername)) {
//					DownloadWorker appointmentWorker = new DownloadWorker(context, clientFacade.getUser(raplaUsername));
//					appointmentWorker.perform();
//			}
//		}
//	}
//
//}
