package org.rapla.client.edit.reservation.sample;

/*
@Extension(provides = ActivityPresenter.class, id = ReservationPresenter.EDIT_ACTIVITY_ID)
@Singleton
public class ReservationEditActivityController  implements ActivityPresenter
{
    final private Provider<ReservationPresenter> presenterProvider;
    final private RaplaFacade facade;
    final private Logger logger;
    private final Map<String, ReservationPresenter> opendPresenter = new HashMap<>();

    @Inject
    public ReservationEditActivityController(Provider<ReservationPresenter> presenterProvider, RaplaFacade facade, Logger logger)
    {
        this.presenterProvider = presenterProvider;
        this.facade = facade;
        this.logger = logger;
    }

    @Override @SuppressWarnings("rawtypes") public boolean startActivity(Activity activity)
    {
        try
        {
            final StorageOperator operator = facade.getOperator();
            final ReferenceInfo<Reservation> info = new ReferenceInfo(activity.getInfo(), Reservation.class);
            final List<ReferenceInfo<Reservation>> referenceInfos =  (List)Collections.singletonList(info);
            final Map<ReferenceInfo<Reservation>, Reservation> entities = operator.getFromId(referenceInfos, false);
            final Collection<Reservation> values = entities.values();
            for (Reservation reservation : values)
            {
                if (reservation != null )
                {
                    final ReservationPresenter alreadyOpendPresenter = opendPresenter.get(reservation.getId());
                    if(alreadyOpendPresenter == null || !alreadyOpendPresenter.isVisible())
                    {
                        final ReservationPresenter newReservationPresenter = presenterProvider.get();
                        newReservationPresenter.edit(reservation, false);
                        opendPresenter.put(reservation.getId(), newReservationPresenter);
                    }
                    return true;
                }
            }
        }
        catch (RaplaException e)
        {
            logger.error("Error initializing activity: " + activity, e);
        }
        return false;
    }
}
*/