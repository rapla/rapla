/*--------------------------------------------------------------------------*
 | Copyright (C) 2006 Gereon Fassbender, Christopher Kohlhaas               |
 |                                                                          |
 | This program is free software; you can redistribute it and/or modify     |
 | it under the terms of the GNU General Public License as published by the |
 | Free Software Foundation. A copy of the license has been included with   |
 | these distribution in the COPYING file, if not go to www.fsf.org         |
 |                                                                          |
 | As a special exception, you are granted the permissions to link this     |
 | program with every library, which license fulfills the Open Source       |
 | Definition as published by the Open Source Initiative (OSI).             |
 *--------------------------------------------------------------------------*/
/** rapla-specific implementation of builder.
* Splits the appointments into day-blocks.
*/

package org.rapla.plugin.abstractcalendar;

import jsinterop.annotations.JsIgnore;
import jsinterop.annotations.JsType;
import org.jetbrains.annotations.NotNull;
import org.rapla.RaplaResources;
import org.rapla.client.internal.AppointmentInfoUI;
import org.rapla.client.internal.RaplaColors;
import org.rapla.components.calendarview.Block;
import org.rapla.components.calendarview.BlockContainer;
import org.rapla.components.calendarview.BuildStrategy;
import org.rapla.components.calendarview.Builder;
import org.rapla.components.util.Assert;
import org.rapla.components.util.DateTools;
import org.rapla.components.util.TimeInterval;
import org.rapla.components.i18n.I18nBundle;
import org.rapla.entities.Category;
import org.rapla.entities.CategoryAnnotations;
import org.rapla.entities.NamedComparator;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.AppointmentFormater;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.Attribute;
import org.rapla.entities.dynamictype.AttributeAnnotations;
import org.rapla.entities.dynamictype.Classifiable;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.entities.dynamictype.SortedClassifiableComparator;
import org.rapla.entities.dynamictype.internal.DynamicTypeImpl;
import org.rapla.facade.CalendarModel;
import org.rapla.facade.CalendarOptions;
import org.rapla.facade.Conflict;
import org.rapla.facade.Conflict.Util;
import org.rapla.facade.RaplaComponent;
import org.rapla.facade.RaplaFacade;
import org.rapla.facade.internal.CalendarModelImpl;
import org.rapla.facade.internal.ConflictImpl;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.TypedComponentRole;
import org.rapla.logger.Logger;
import org.rapla.scheduler.Promise;
import org.rapla.storage.PermissionController;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

public class RaplaBuilder
    implements
        Builder
        ,Cloneable
{

    private Collection<Reservation> selectedReservations;
    private Collection<Allocatable> selectedAllocatables = new LinkedHashSet<>();

    private boolean bExceptionsExcluded = false;
    private boolean bResourceVisible = true;
    private boolean bPersonVisible = true;
    private boolean bRepeatingVisible = true;
    private boolean bTimeVisible = true; //Shows time <from - till> in top of all HTML- and Swing-View Blocks
    private boolean splitByAllocatables = false;
    private HashMap<Allocatable,String> colors = new HashMap<>(); //This currently only works with HashMap
    private User editingUser;
    private boolean isResourceColoring;
    private boolean isEventColoring;
    private boolean nonFilteredEventsVisible;
    private BlockCreator blockCreator = (blockContext, start, end) -> new RaplaBlock(blockContext, start, end);
    Map<Allocatable,Collection<Appointment>> bindings;

    /** default buildStrategy is {@link GroupAllocatablesStrategy}.*/
    BuildStrategy buildStrategy;

    //HashSet<Reservation> allReservationsForAllocatables = new HashSet<Reservation>();

    
    public static final TypedComponentRole<Boolean> SHOW_TOOLTIP_CONFIG_ENTRY = new TypedComponentRole<>("org.rapla.showTooltips");

	Map<Appointment,Set<Appointment>> conflictingAppointments;
    
	final private RaplaLocale raplaLocale;
	final private RaplaFacade raplaFacade;
	final private RaplaResources i18n;
	final private Logger logger;
	final private AppointmentFormater appointmentFormater;

	@Inject
	public RaplaBuilder(RaplaLocale raplaLocale, RaplaFacade raplaFacade, RaplaResources i18n, Logger logger, AppointmentFormater appointmentFormater) {
        Locale locale = raplaLocale.getLocale();
        buildStrategy = new GroupAllocatablesStrategy( locale );
        this.raplaLocale = raplaLocale;
        this.raplaFacade = raplaFacade;
        this.i18n = i18n;
        this.logger = logger;
        this.appointmentFormater = appointmentFormater;
	}

    public void setBlockCreator(BlockCreator blockCreator) {
        this.blockCreator = blockCreator;
    }

    protected RaplaLocale getRaplaLocale()
    {
        return raplaLocale;
    }
    
    protected RaplaFacade getClientFacade()
    {
        return raplaFacade;
    }
    
    protected RaplaResources getI18n()
    {
        return i18n;
    }
    
    protected Logger getLogger() {
        return logger;
    }
    
    public Promise<RaplaBuilder> initFromModel(CalendarModel model, Date startDate, Date endDate)
    {
        final RaplaBuilder builder = this;
        final TimeInterval interval = new TimeInterval( startDate, endDate);
        final Promise<Map<Allocatable, Collection<Appointment>>> appointmentBindungsPromise = model.queryAppointmentBindings(interval);
        final Promise<RaplaBuilder> builderPromise = appointmentBindungsPromise.thenApply((appointmentBindings) -> {
            Collection<Conflict> conflictsSelected = new ArrayList<>();
            conflictsSelected.addAll( ((CalendarModelImpl)model).getSelectedConflicts());
            bindings = appointmentBindings;
            Collection<Allocatable> allocatables ;
            if ( !conflictsSelected.isEmpty() )
            {
                allocatables = Util.getAllocatables( conflictsSelected );
                Collection<Appointment> all = new LinkedHashSet<>();
                for ( Collection<Appointment> appointments: bindings.values())
                {
                    all.addAll( appointments);
                }
                conflictingAppointments = ConflictImpl.getMap( conflictsSelected, all);
            }
            else
            {
                Collection<Appointment> all = new LinkedHashSet<>();
                allocatables = new ArrayList<>(bindings.keySet());
                Collections.sort( (List)allocatables, new SortedClassifiableComparator(raplaLocale.getLocale()));
                for ( Collection<Appointment> appointments: bindings.values())
                {
                    all.addAll( appointments);
                }
                conflictingAppointments = null;
                
//            long time = System.currentTimeMillis();
                
//            getLogger().info("Kram took " + (System.currentTimeMillis() - time) + " ms ");
                
            }
            
            
            if ( conflictsSelected.isEmpty() )
            {
                // FIXME check if needed
//        	if ( allocatables.isEmpty() || startDate == null)
                //        	{
                //        		filteredReservations = Arrays.asList( model.getReservations(  startDate, endDate ));
                //        	}
                //        	else
                //        	{
                //        		filteredReservations = ((CalendarModelImpl)model).restrictReservations( allReservationsForAllocatables);
                //        	}
            }
            selectedReservations = CalendarModelImpl.getAllReservations(bindings );
            User user = model.getUser();
            CalendarOptions calendarOptions = RaplaComponent.getCalendarOptions( user, getClientFacade());
            nonFilteredEventsVisible = calendarOptions.isNonFilteredEventsVisible();
            isResourceColoring =calendarOptions.isResourceColoring();
            isEventColoring =calendarOptions.isEventColoring();
            
            setEditingUser(user);
            setExceptionsExcluded( !calendarOptions.isExceptionsVisible());
            
            /* Uncomment this to color allocatables in the reservation view
        if ( allocatables.size() == 0) {
            allocatables = new ArrayList();
            for (int i=0;i< reservations.size();i++) {
                Reservation r = (Reservation) reservations.get( i );
                Allocatable[] a = r.getAllocatables();
                for (int j=0;j<a.length;j++) {
                    if ( !allocatables.contains( a[j] )) {
                        allocatables.add( a[j]);
                    }
                }
            }
        }*/
            
            selectedAllocatables.clear();
            if (allocatables != null ) {
                List<Allocatable> list = new ArrayList<>(allocatables);
                Collections.sort( list, new NamedComparator<>(getRaplaLocale().getLocale()));
                selectedAllocatables.addAll(new HashSet<>(list));
            }
            createColorMap();
            return builder;
        });
        return builderPromise;
    }

    public boolean isNonFilteredEventsVisible() {
		return nonFilteredEventsVisible;
	}

	public void setNonFilteredEventsVisible(boolean nonFilteredEventsVisible) {
		this.nonFilteredEventsVisible = nonFilteredEventsVisible;
	}

	public void setSmallBlocks( boolean isSmallView) {
        setTimeVisible(  isSmallView );
        setPersonVisible( !isSmallView );
        setResourceVisible( !isSmallView );
    }

    public void setSplitByAllocatables( boolean enable) {
        splitByAllocatables = enable;
    }

    public void setExceptionsExcluded( boolean exclude) {
        this.bExceptionsExcluded = exclude;
    }

    protected boolean isExceptionsExcluded() {
        return bExceptionsExcluded;
    }

    public void setEditingUser(User editingUser) {
        this.editingUser = editingUser;
    }

    public User getEditingUser() {
        return this.editingUser;
    }

    public void setBuildStrategy(BuildStrategy strategy) {
        this.buildStrategy = strategy;
    }

    public void setTimeVisible(boolean bTimeVisible) {
        this.bTimeVisible = bTimeVisible;
    }

    public boolean isTimeVisible() {
        return bTimeVisible;
    }

    private void setResourceVisible(boolean bVisible) {
        this.bResourceVisible = bVisible;
    }

    private void setPersonVisible(boolean bVisible) {
        this.bPersonVisible = bVisible;
    }

    public void setRepeatingVisible(boolean bVisible) {
        this.bRepeatingVisible = bVisible;
    }

    public Collection<Allocatable> getAllocatables() {
        return selectedAllocatables;
    }

    /**
        Map enthaelt die fuer die resourcen-darstellung benutzten farben
        mit resource-id (vom typ Integer) als schluessel.
        erst nach rebuild() verfuegbar.
    */
    Map<Allocatable,String> getColorMap() {
        return colors;
    }

    private void createColorMap()
    {
        colors.clear();
        List<Allocatable> arrayList = new ArrayList<>(selectedAllocatables);
        Comparator<Allocatable> comp = (o1, o2) -> {
            if (o1.hashCode()>o2.hashCode())
                return -1;
            if (o1.hashCode()<o2.hashCode())
                return 1;
            return 0;
        };
        Collections.sort(arrayList,comp);
        Iterator<Allocatable> it = arrayList.iterator();
        int i=0;
        while (it.hasNext()) {
            Allocatable allocatable =  it.next();
            DynamicType type = allocatable.getClassification().getType();
            String annotation = type.getAnnotation(DynamicTypeAnnotations.KEY_COLORS);
        	String color =null;
        	if (annotation  == null)
        	{
        		annotation =  ((DynamicTypeImpl)type).getFirstAttributeWithAnnotation(AttributeAnnotations.KEY_COLOR) != null ? DynamicTypeAnnotations.VALUE_COLORS_COLOR_ATTRIBUTE: DynamicTypeAnnotations.VALUE_COLORS_AUTOMATED;
        	}
        	color = getColorForClassifiable( allocatable );
            if ( color == null && annotation.equals(DynamicTypeAnnotations.VALUE_COLORS_AUTOMATED))
            {
            	color = RaplaColors.getResourceColor(i++);
            }
            else if (  annotation.equals(DynamicTypeAnnotations.VALUE_COLORS_DISABLED))
            {
            	color = null;
            }
            
            if ( color != null)
            {
            	colors.put(allocatable, color);
            }
        }
    }

    static String getColorForClassifiable( Classifiable classifiable ) {
        Classification c = classifiable.getClassification();
        Attribute colorAttribute =((DynamicTypeImpl)c.getType()).getFirstAttributeWithAnnotation(AttributeAnnotations.KEY_COLOR);
        String annotation = c.getType().getAnnotation(DynamicTypeAnnotations.KEY_COLORS);
        if ( annotation != null && annotation.equals( DynamicTypeAnnotations.VALUE_COLORS_DISABLED))
        {
        	return null;
        }
        String color = null;
        if ( colorAttribute != null) {
            Object hexValue = c.getValueForAttribute( colorAttribute );
            if ( hexValue != null ) {
                if ( hexValue instanceof Category) {
                    hexValue = ((Category) hexValue).getAnnotation( CategoryAnnotations.KEY_NAME_COLOR );
                }
                if ( hexValue != null) {
                    color = hexValue.toString();
                }
            }
        }
        if ( color == null || color.trim().length() == 0)
        {
            return null;
        }
        return color;
    }

    static public class SplittedBlock extends AppointmentBlock
    {
        final private boolean splitStart;
        final private boolean splitEnd;
    	public SplittedBlock(AppointmentBlock original, long start, long end, Appointment appointment, boolean isException, boolean splitStart,
                boolean splitEnd) {
			super(start, end, appointment, isException);
			this.original = original;
            this.splitStart = splitStart;
            this.splitEnd = splitEnd;
        }
    	
    	public AppointmentBlock getOriginal() {
			return original;
		}

		AppointmentBlock original;
    }

    @JsIgnore
    static public List<AppointmentBlock> splitBlocks(Collection<AppointmentBlock> preparedBlocks, Date startDate, Date endDate, int offsetMinutes) {
        List<AppointmentBlock> result = new ArrayList<>();
        for (AppointmentBlock block:preparedBlocks) {
            long blockStart = block.getStart();
            long blockEnd = block.getEnd();
            Appointment appointment = block.getAppointment();
            boolean isException = block.isException();
            final long offsetMillis = offsetMinutes * DateTools.MILLISECONDS_PER_MINUTE;
            BiFunction<Long, Long, Boolean> shouldSplit = ( start, end) -> !DateTools.isSameDay( start -offsetMillis, end- offsetMillis);
            Function<Long, Long> minStart = ( date) -> DateTools.cutDate( date -offsetMillis ) + offsetMillis;
            Function<Long, Long> maxEnd = ( date) -> DateTools.fillDate( date  - offsetMillis)-1 + offsetMillis;


            if (shouldSplit.apply(blockStart, blockEnd) ) {
                long firstBlockDate = Math.max(blockStart, startDate.getTime());
                long lastBlockDate = Math.min(blockEnd, endDate.getTime());
                long currentBlockDate = firstBlockDate;
                while ( currentBlockDate >= blockStart && minStart.apply( currentBlockDate )  < lastBlockDate) {
                    final boolean splitStart =shouldSplit.apply(blockStart, currentBlockDate);
                    final long start = splitStart ? minStart.apply(currentBlockDate): blockStart;
                    final boolean splitEnd = shouldSplit.apply(blockEnd, currentBlockDate) || minStart.apply(blockEnd) == blockEnd;
                    final long end = splitEnd ? maxEnd.apply( currentBlockDate ): blockEnd;
                    //System.out.println("Adding Block " + new Date(start) + " - " + new Date(end));
                    result.add ( new SplittedBlock(block,start, end, appointment,isException, splitStart, splitEnd));
                    currentBlockDate+= DateTools.MILLISECONDS_PER_DAY;
                }
            } else {
                result.add( block);
            }
        }
        return result;
    }


    /** selects all blocks that should be visible and calculates the max start- and end-time  */
    public PreperationResult prepareBuild(Date start,Date end) {
        start = new Date( start.getTime() );
        end = new Date( end.getTime() );
        boolean excludeExceptions = isExceptionsExcluded();
    	boolean nonFilteredEventsVisible = isNonFilteredEventsVisible();

        //long time = System.currentTimeMillis();
        Collection<Appointment> appointments = CalendarModelImpl.getAllAppointments(bindings);
        // FIXME
        if ( nonFilteredEventsVisible)
        {

        }
        else
        {
        }
        //= AppointmentImpl.getAppointments(	nonFilteredEventsVisible ? allReservations : selectedReservations, selectedAllocatables);
        //logger.info( "Get appointments took " + (System.currentTimeMillis() - time) + " ms.");
        // Add appointment to the blocks
        final List<AppointmentBlock> blocks = new ArrayList<>();
        for (Appointment app:appointments)
        {
            if ( excludeExceptions)
            {
                app.createBlocksExcludeExceptions(start, end, blocks);
            }
            else
            {
                app.createBlocks(start, end, blocks);
            }
        }
        int offsetMinutes = buildStrategy.getOffsetMinutes();
        List<AppointmentBlock> preparedBlocks = splitBlocks(blocks, start, end, offsetMinutes);
        int minHour = 0 + offsetMinutes * 60;
        int maxHour = 24 + offsetMinutes * 60;
        // calculate new start and end times
        int max =minHour * 60;
        int min =maxHour*60;
        for (AppointmentBlock block:blocks)
        {
            int starthour = DateTools.getHourOfDay(block.getStart());
            int startminute = DateTools.getMinuteOfHour(block.getStart());
            int endhour = DateTools.getHourOfDay(block.getEnd());
            int endminute = DateTools.getMinuteOfHour(block.getEnd());
            if ((starthour != 0 || startminute != 0)  && starthour*60 + startminute<min)
                min = starthour * 60 + startminute;
            if ((endhour >0 || endminute>0) && (endhour *60 + endminute)<min  )
                min = Math.max(minHour,endhour-1) * 60 + endminute;
            if ((endhour != 0 || endminute != 0) && (endhour != 23 || endminute!=59) && (endhour *60 + endminute)>max)
                max = Math.min(maxHour*60 , endhour  * 60 + endminute);
            if (starthour>=max)
                max = Math.min(maxHour*60 , starthour *60 + startminute);
        }
        return new PreperationResult( min, max,preparedBlocks);
    }

    public void build(BlockContainer blockContainer, Date startDate, Collection<AppointmentBlock> preparedBlocks) {

        List<Block> blocks = createBlocks(preparedBlocks, blockCreator);
        //long time = System.currentTimeMillis();
        buildStrategy.build(blockContainer, blocks, startDate);
        //logger.info( "Build strategy took " + (System.currentTimeMillis() - time) + " ms.");

    }

    public interface BlockCreator
    {
        Block createBlock(RaplaBlockContext blockContext, Date start, Date end);
    }

    @NotNull
    public List<Block> createBlocks(Collection<AppointmentBlock> preparedBlocks, BlockCreator blockCreator)
    {
        ArrayList<Block> blocks = new ArrayList<>();
        {
            //long time = System.currentTimeMillis();
            AppointmentInfoUI appointmentInfoUI = new AppointmentInfoUI(i18n,raplaLocale, raplaFacade,logger, appointmentFormater);
        	BuildContext buildContext = new BuildContext(this, appointmentInfoUI, blocks);
            Assert.notNull(preparedBlocks, "call prepareBuild first");
            for (AppointmentBlock block:preparedBlocks)
            {
                Date start = new Date( block.getStart() );
                Date end = new Date( block.getEnd() );
                RaplaBlockContext[] blockContext = getBlocksForAppointment( block, buildContext );
                for ( int j=0;j< blockContext.length; j++) {
                    blocks.add( blockCreator.createBlock(blockContext[j], start, end));
                }
            }
            //logger.info( "Block creation took " + (System.currentTimeMillis() - time) + " ms.");
        }
        return blocks;
    }

    private RaplaBlockContext[] getBlocksForAppointment(AppointmentBlock block, BuildContext buildContext) {
    	Appointment appointment = block.getAppointment();
        final Reservation reservation = appointment.getReservation();
        boolean isBlockSelected = selectedReservations.contains(reservation);
        boolean isConflictsSelected = isConflictsSelected();
		if ( !isBlockSelected && (!nonFilteredEventsVisible && !isConflictsSelected))
        {
            return new RaplaBlockContext[] {};
        }
        if ( isConflictsSelected)
        {
            boolean found = false;
            Collection<Appointment> collection = conflictingAppointments.get(appointment);
            if ( collection != null)
            {
	            for (Appointment conflictingApp:collection)
	            {
                	if ( conflictingApp.overlapsBlock( block))
                	{
                		found = true;
                		break;
                	}
	            }
            }
            if ( !found)
            {
                isBlockSelected = false;
            }
        }
      
        boolean isAnonymous = isAnonymous(buildContext.user, appointment);
        RaplaBlockContext firstContext = new RaplaBlockContext( block, this, buildContext, null, isBlockSelected, isAnonymous );
        List<Allocatable> selectedAllocatables = firstContext.getSelectedAllocatables();
        if ( !splitByAllocatables || selectedAllocatables.size() < 2) {
            return new RaplaBlockContext[] { firstContext };
        }
        RaplaBlockContext[] context = new RaplaBlockContext[ selectedAllocatables.size() ];

        for ( int i= 0;i<context.length;i ++) {
            context[i] = new RaplaBlockContext( block, this, buildContext, selectedAllocatables.get( i ), isBlockSelected, isAnonymous);
        }
        return context;
    }

    protected PermissionController getPermissionController()
    {
        return getClientFacade().getPermissionController();
    }
	protected boolean isAnonymous(User user,Appointment appointment) {
        return !getPermissionController().canRead(appointment, user);
	}

    private boolean isMovable(Reservation reservation) {
        return selectedReservations.contains( reservation ) && getPermissionController().canModify(reservation, editingUser);
    }

    public boolean isConflictsSelected() {
        return conflictingAppointments != null;
    }

    /** This context contains the shared information for all RaplaBlocks.*/
    @JsType
    public static class BuildContext {
        boolean bResourceVisible = true;
        boolean bPersonVisible = true;
        boolean bRepeatingVisible = true;
        boolean bTimeVisible = false;
        boolean conflictsSelected = false;
        Map<Allocatable,String> colors;
        RaplaResources i18n;
        RaplaLocale raplaLocale;
        Logger logger;
        User user;
        List<Block> blocks;
		private boolean isResourceColoring;
		private boolean isEventColoring;
        private boolean showTooltips;
        final AppointmentInfoUI appointmentInfoUI;
        PermissionController permissionController;
        
        @SuppressWarnings("unchecked")
		public BuildContext(RaplaBuilder builder, AppointmentInfoUI appointmentInfoUI, List<Block> blocks)
        {
        	this.blocks = blocks;
        	this.appointmentInfoUI = appointmentInfoUI;
            this.raplaLocale = builder.getRaplaLocale();
            this.bResourceVisible = builder.bResourceVisible;
            this.bPersonVisible= builder.bPersonVisible;
            this.bTimeVisible= builder.isTimeVisible();
            this.bRepeatingVisible= builder.bRepeatingVisible;
            this.colors = (Map<Allocatable,String>) builder.colors.clone();
            this.i18n =builder.getI18n();
            this.logger = builder.getLogger();
            this.user = builder.editingUser;
            this.conflictsSelected = builder.isConflictsSelected();
            this.isResourceColoring = builder.isResourceColoring;
            this.isEventColoring = builder.isEventColoring;
            this.permissionController = builder.getPermissionController();
            try {
                this.showTooltips = builder.getClientFacade().getPreferences(user).getEntryAsBoolean(RaplaBuilder.SHOW_TOOLTIP_CONFIG_ENTRY, true);
            } catch (RaplaException e) {
                this.showTooltips = true;
                getLogger().error(e.getMessage(), e);
            }
        }

        public RaplaLocale getRaplaLocale() {
            return raplaLocale;
        }

        public List<Block> getBlocks()
        {
        	return blocks;
        }

        public I18nBundle getI18n() {
            return i18n;
        }
        public boolean isTimeVisible() {
            return bTimeVisible;
        }

        public boolean isPersonVisible() {
            return bPersonVisible;
        }

        public boolean isRepeatingVisible() {
            return bRepeatingVisible;
        }

        public boolean isResourceVisible() {
            return bResourceVisible;
        }

        public String lookupColorString(Allocatable allocatable) {
            if (allocatable == null)
                return RaplaColors.DEFAULT_COLOR_AS_STRING;
            return colors.get(allocatable);
        }

        public boolean isConflictSelected() 
        {
            return conflictsSelected;
        }

		public boolean isResourceColoringEnabled() {
			return isResourceColoring;
		}
		
		public boolean isEventColoringEnabled() {
			return isEventColoring;
		}

		public Logger getLogger() {
			return logger;
		}


        public boolean isShowToolTips() {
            return showTooltips;
        }

        AppointmentInfoUI getAppointmentInfo()
        {
            return appointmentInfoUI;
        }

        public PermissionController getPermissionController()
        {
            return permissionController;
        }
    }

    /** This context contains the shared information for one particular RaplaBlock.*/
    public static class RaplaBlockContext {
        Set<Allocatable> selectedMatchingAllocatables = new LinkedHashSet<>(3);
        ArrayList<Allocatable> matchingAllocatables = new ArrayList<>(3);
        AppointmentBlock appointmentBlock;
        boolean movable;
        BuildContext buildContext;
        boolean isBlockSelected;
        boolean isAnonymous;
        boolean splitStart = false;
        boolean splitEnd = false;

        public RaplaBlockContext(AppointmentBlock appointmentBlock,RaplaBuilder builder,BuildContext buildContext, Allocatable selectedAllocatable, boolean isBlockSelected, boolean isAnonymous) {
            this.buildContext = buildContext;
            this.isAnonymous = isAnonymous;
            if ( appointmentBlock instanceof SplittedBlock)
            {
                final SplittedBlock splittedBlock = (SplittedBlock) appointmentBlock;
                this.appointmentBlock = splittedBlock.getOriginal();
                splitStart = splittedBlock.splitStart;
                splitEnd = splittedBlock.splitEnd;
            }
            else
            {
            	this.appointmentBlock = appointmentBlock;
            }
            Appointment appointment =appointmentBlock.getAppointment();
            this.isBlockSelected = isBlockSelected;
            Reservation reservation = appointment.getReservation();
            if(isBlockSelected)
            	this.movable = builder.isMovable( reservation );
            // Prefer resources when grouping
            addAllocatables(builder, reservation.getResources(), selectedAllocatable);
            addAllocatables(builder, reservation.getPersons(), selectedAllocatable);
        }

        public boolean isSplitStart()
        {
            return splitStart;
        }

        public boolean isSplitEnd()
        {
            return splitEnd;
        }

        private void addAllocatables(RaplaBuilder builder, Allocatable[] allocatables,Allocatable selectedAllocatable) {
        	Appointment appointment =appointmentBlock.getAppointment();
        	for (Allocatable allocatable : allocatables)
            {
        	    if(appointment.getReservation().hasAllocatedOn(allocatable, appointment))
        	    {
        	        matchingAllocatables.add(allocatable);
        	    }
            }
            for(Allocatable alloc : builder.bindings.keySet())
            {
                final Collection<Appointment> appointments = builder.bindings.get(alloc);
                if(appointments.contains(appointment))
                {
                    if ( selectedAllocatable == null ||  selectedAllocatable.equals( alloc) ) {
                        selectedMatchingAllocatables.add(alloc);
                    }
                }
            }
        }

        public boolean isMovable() {
            return movable;// && !isAnonymous;
        }
        
        public AppointmentBlock getAppointmentBlock() {
        	return appointmentBlock;
        }

        public Appointment getAppointment() {
        	Appointment appointment =appointmentBlock.getAppointment();
        	return appointment;
        }

        public List<Allocatable> getSelectedAllocatables() {
            return new ArrayList<>(selectedMatchingAllocatables);
        }

        public List<Allocatable> getAllocatables() {
            return matchingAllocatables;
        }

        public boolean isVisible(Allocatable allocatable) {
            User user = buildContext.user;
            final PermissionController permissionController = getBuildContext().getPermissionController();

            if ( user != null && !permissionController.canReadOnlyInformation(allocatable, user) ) {
                return false;
            }
            return matchingAllocatables.contains(allocatable);
        }

        public BuildContext getBuildContext() {
            return buildContext;
        }

        /**
         * @return null if no allocatables found
         */
        public Allocatable getGroupAllocatable() {
            // Look if block belongs to a group according to the selected allocatables
            List<Allocatable> allocatables = getSelectedAllocatables();
            // Look if block belongs to a group according to its reserved allocatables
            if ( allocatables.size() == 0)
                allocatables = getAllocatables();
            if ( allocatables.size() == 0)
                return null;
            return allocatables.get( 0 );
        }

        public boolean isBlockSelected() {
            return isBlockSelected;
        }

        public boolean isAnonymous() {
        	return isAnonymous;
        }
        
        public String getTooltip() 
        {
            AppointmentInfoUI factory = getBuildContext().getAppointmentInfo();
            Appointment appointment = getAppointment();
            User user = getBuildContext().user;
            return factory.getTooltip( appointment, user);
        }

    }

}


