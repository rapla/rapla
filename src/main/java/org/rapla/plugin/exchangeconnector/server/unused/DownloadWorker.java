/**
 *
 */
package org.rapla.plugin.exchangeconnector.server.unused;


/**
 * This worker is used for the retrieval of new appointments from the Exchange Server
 *
 * @author lutz
 */
//public class DownloadWorker extends EWSWorker {
//    private Preferences preferences;
//
//    /**
//     * The constructor
//     *
//     * @param raplaUsername
//     * @throws Exception
//     */
//    public DownloadWorker(RaplaContext context, User raplaUsername) throws Exception {
//        super(context, raplaUsername);
//    }
//
//    /**
//     * This method holds the core functionality of the worker.
//     * It retrieves all appointments for the current user from the Exchange Server
//     * and successively adds them to the Rapla-database
//     *
//     * @throws Exception
//     */
//    public synchronized void perform() throws Exception {
//
//        //getFacade().switchTo(getRaplaUser());
//
//        preferences = getClientFacade().getPreferences(getRaplaUser());
//        FindItemsResults<Item> findItemResults = null;
//        int nextPageOffset = 0;
//        do {
//            findItemResults = getFindItemResults(nextPageOffset);
//            if (findItemResults == null || findItemResults.getTotalCount() == 0) {
//                break;
//            }
//            Set<Appointment> newExchangeAppointments = getNewExchangeAppointments(findItemResults);
//            HashMap<String, Reservation> newRaplaReservations = createEquivalentRaplaReservations(newExchangeAppointments);
//            addAndStoreToRapla(newRaplaReservations);
//
//            nextPageOffset = (findItemResults.isMoreAvailable()) ? findItemResults.getNextPageOffset() : 0;
//        } while (nextPageOffset > 0);
//    }
//
//    /**
//     * @param newExchangeAppointments
//     * @return
//     * @throws Exception
//     */
//    private HashMap<String, Reservation> createEquivalentRaplaReservations(Set<Appointment> newExchangeAppointments) throws Exception {
//        HashMap<String, Reservation> newRaplaReservations = new HashMap<String, Reservation>();
//        for (Appointment exchangeAppointment : newExchangeAppointments) {
//            if (!isRaplaItem(exchangeAppointment)) {
//                Reservation raplaReservation = createEquivalentRaplaReservation(exchangeAppointment);
//                if (raplaReservation != null)
//                    newRaplaReservations.put(exchangeAppointment.getId().getUniqueId(), raplaReservation);
//                else
//                    throw new RaplaException("Could not find suitable event type for import. Please contact administrator!");
//            }
//        }
//        return newRaplaReservations;
//    }
//
//    /**
//     * @param newRaplaReservations
//     * @throws Exception
//     */
//    private void addAndStoreToRapla(HashMap<String, Reservation> newRaplaReservations) throws Exception {
//        for (String exchangeId : newRaplaReservations.keySet()) {
//            addAndStoreToRapla(exchangeId, newRaplaReservations.get(exchangeId));
//        }
//    }
//
//    /**
//     * @param findItemResults
//     * @return
//     * @throws Exception
//     */
//    private Set<Appointment> getNewExchangeAppointments(FindItemsResults<Item> findItemResults) throws Exception {
//        Set<Appointment> newExchangeAppointments = new HashSet<Appointment>();
//        for (Item item : findItemResults.getItems()) {
//            Appointment newBoundAppointment = Appointment.bind(getService(), item.getId());
//            if (!newBoundAppointment.getAppointmentType().equals(AppointmentType.Single)
//                    && !newBoundAppointment.getAppointmentType().equals(AppointmentType.RecurringMaster)) {
//                newBoundAppointment = Appointment.bindToRecurringMaster(getService(), item.getId());
//            }
//            if (!newBoundAppointment.getIsCancelled()) {
//                newExchangeAppointments.add(newBoundAppointment);
//            }
//        }
//        return newExchangeAppointments;
//    }
//
//    private synchronized boolean isRaplaItem(Appointment appointment) throws Exception {
//        String bodyString = MessageBody.getStringFromMessageBody(appointment.getBody());
//        if (bodyString != null && !bodyString.isEmpty())
//            return bodyString.contains(RAPLA_NOSYNC_KEYWORD);
//        else
//            return false;
//    }
//
//    /**
//     * @param pageOffset
//     * @return
//     * @throws Exception
//     */
//    private synchronized FindItemsResults<Item> getFindItemResults(int pageOffset) throws Exception {
//
//        final ItemView downloadView = (pageOffset > 0) ? new ItemView(ExchangeConnectorPlugin.EXCHANGE_FINDITEMS_PAGESIZE, pageOffset) : new ItemView(ExchangeConnectorPlugin.EXCHANGE_FINDITEMS_PAGESIZE);
//
//        final PropertySet propertySet = new PropertySet(BasePropertySet.IdOnly, raplaAppointmentPropertyDefinition);
//        downloadView.setPropertySet(propertySet);
//
//        final SearchFilter hasRaplaCategoryFilter = new SearchFilter.IsEqualTo(AppointmentSchema.Categories, ExchangeConnectorPlugin.EXCHANGE_APPOINTMENT_CATEGORY);
//        SearchFilter hasNotRaplaCategoryFilter = new SearchFilter.Not(hasRaplaCategoryFilter);
//
//        final SearchFilter extendedPropertyFilter = new SearchFilter.Exists(raplaAppointmentPropertyDefinition);
//        SearchFilter extendedPropertyFilterNot = new SearchFilter.Not(extendedPropertyFilter);
//
//        // check if user has defined special import category, otherwise skip
//        final FindItemsResults<Item> findItemResults;
//        final String filterCategory = preferences.getEntryAsString(ExchangeConnectorPlugin.EXCHANGE_INCOMING_FILTER_CATEGORY_KEY, ExchangeConnectorPlugin.DEFAULT_EXCHANGE_INCOMING_FILTER_CATEGORY);
//        if (filterCategory != null && !filterCategory.trim().isEmpty()) {
//            // category filter
//            final SearchFilter hasImportCategoryFilter = new SearchFilter.IsEqualTo(AppointmentSchema.Categories, filterCategory.trim());
//
//            final Date from = ExchangeConnectorPlugin.getSynchingPeriodPast(new Date());
//            final Date to = ExchangeConnectorPlugin.getSynchingPeriodFuture(new Date());
//
//            final SearchFilter fromFilter = new SearchFilter.IsGreaterThanOrEqualTo(AppointmentSchema.Start, from);
//            final SearchFilter toFilter = new SearchFilter.IsLessThanOrEqualTo(AppointmentSchema.Start, to);
//
//            final SearchFilter filterCollection = new SearchFilter.SearchFilterCollection(LogicalOperator.And,
//                    fromFilter,
//                    toFilter,
//                    hasNotRaplaCategoryFilter,
//                    hasImportCategoryFilter,
//                    extendedPropertyFilterNot);
//
//
//            findItemResults = getService().findItems(WellKnownFolderName.Calendar, filterCollection, downloadView);
//        } else {
//            findItemResults = null;
//        }
//
//        return findItemResults;
//    }
//
//
//    /**
//     * This method fulfills the complex task to store an appointment coming from the Exchange Server to Rapla
//     *
//     * @param exchangeId
//     * @param raplaReservation : {@link Appointment}
//     * @throws Exception
//     */
//    private synchronized void addAndStoreToRapla(String exchangeId, Reservation raplaReservation) throws Exception {
//        String raplaUsername = getRaplaUser().getUsername();
//        for (org.rapla.entities.domain.Appointment tmpAppointment : raplaReservation.getAppointments()) {
//            ExchangeAppointmentStorage.getInstance().addAppointment(tmpAppointment, exchangeId, raplaUsername, true);
//        }
//        ExchangeAppointmentStorage.getInstance().save();
//        getLogger().info("Adding appointment for " + raplaUsername + " from exchange: " + raplaReservation);
//        getClientFacade().store(raplaReservation);
//    }
//
//    /**
//     * @param exchangeAppointment
//     * @return
//     * @throws Exception
//     */
//    private Reservation createEquivalentRaplaReservation(Appointment exchangeAppointment) throws Exception {
//        Reservation raplaReservation = null;
//        RaplaFacade currentClientFacade = getClientFacade();
//        DynamicType importEventType = ExchangeConnectorPlugin.getImportEventType(currentClientFacade);
//
//        if (importEventType != null) {
//            final Classification classification = importEventType.newClassification();
//            raplaReservation = currentClientFacade.newReservation(classification,getRaplaUser());
//
//            final AttendeeCollection resources = exchangeAppointment.getResources();
//            for (Attendee resource : resources) {
//                final String address = resource.getAddress();
//                try {
//                    final DynamicType roomType = currentClientFacade.getDynamicType(ExchangeConnectorPlugin.ROOM_TYPE);
//                    final Allocatable[] allocatables = currentClientFacade.getAllocatables(
//                            new ClassificationFilter[]{
//                                    roomType.newClassificationFilter()
//                            }
//                    );
//                    for (Allocatable allocatable : allocatables) {
//                        try {
//                            final Object email = allocatable.getClassification().getValue(ExchangeConnectorPlugin.RAPLA_EVENT_TYPE_ATTRIBUTE_EMAIL);
//                            if (email != null && email.toString().equalsIgnoreCase(address)){
//                                raplaReservation.addAllocatable(allocatable);
//                            }
//                        } finally {
//                            //this might happen since not all resources have mail attributes
//                        }
//                    }
//                } catch (RaplaException e) {
//                    getLogger().error(e.getMessage(), e);
//                }
//            }
//            // mask private titles
//            if (exchangeAppointment.getSensitivity().equals(Sensitivity.Private) || ExchangeConnectorPlugin.EXCHANGE_ALWAYS_PRIVATE) {
//                raplaReservation.getClassification().setValue(ExchangeConnectorPlugin.RAPLA_EVENT_TYPE_ATTRIBUTE_TITLE, ExchangeConnectorPlugin.EXCHANGE_APPOINTMENT_PRIVATE_NAME_IN_RAPLA);
//            } else {
//                raplaReservation.getClassification().setValue(ExchangeConnectorPlugin.RAPLA_EVENT_TYPE_ATTRIBUTE_TITLE, exchangeAppointment.getSubject());
//            }
//
//            if (getRaplaUser().getPerson() != null)
//                raplaReservation.addAllocatable(getRaplaUser().getPerson());
//
//            boolean isRecurring;
//
//            try {
//                isRecurring = exchangeAppointment.getRecurrence() != null;
//            } catch (Exception e) {
//                isRecurring = false;
//            }
//            if (isRecurring) {
//                Calendar oneYearAhead = Calendar.getInstance();
//                oneYearAhead.add(Calendar.YEAR, 1);
//                int occurenceIndex = 1;
//                Appointment occurence = null;
//                do {
//                    try {
//                        occurence = Appointment.bindToOccurrence(getService(), exchangeAppointment.getId(), occurenceIndex);
//                        if (occurence.getStart().after(oneYearAhead.getTime())) {
//                            break;
//                        }
//                        org.rapla.entities.domain.Appointment occurenceAppointment = createRaplaAppointment(occurence);
//                        raplaReservation.addAppointment(occurenceAppointment);
//                    } catch (microsoft.exchange.webservices.data.ServiceResponseException e) {
//                        //this exception is caused, when the occurrence is an exception item
//                        if (e.getErrorCode().toString().equals("ErrorCalendarOccurrenceIndexIsOutOfRecurrenceRange"))
//                            break;
//                    }
//                    occurenceIndex++;
//                } while (true);
//
//
//            } else {
//                org.rapla.entities.domain.Appointment appointment = createRaplaAppointment(exchangeAppointment);
//                raplaReservation.addAppointment(appointment);
//            }
//        }
//        return raplaReservation;
//    }
//
//    /**
//     * @param exchangeAppointment
//     * @return
//     * @throws Exception
//     */
//    private org.rapla.entities.domain.Appointment createRaplaAppointment(Appointment exchangeAppointment) throws Exception {
//    	RaplaLocale raplaLocale = getRaplaLocale();
//    	TimeZone timeZone = raplaLocale.getImportExportTimeZone();
//    	Date startDate = raplaLocale.toRaplaTime(timeZone, exchangeAppointment.getStart());
//        Date endDate = raplaLocale.toRaplaTime(timeZone,exchangeAppointment.getEnd());
//        org.rapla.entities.domain.Appointment raplaAppointment = getClientFacade().newAppointment(startDate, endDate, getRaplaUser());
//
//        if (exchangeAppointment.getIsAllDayEvent()) {
//            raplaAppointment.setWholeDays(true);
//        }
//        return raplaAppointment;
//    }
//}
