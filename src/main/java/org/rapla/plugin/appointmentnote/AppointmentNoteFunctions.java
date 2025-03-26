    /*
     * Copyright [2024] [Liselotte Lichtenstein, Christopher Kohlhaas]
     *
     * Licensed under the Apache License, Version 2.0 (the "License");
     * you may not use this file except in compliance with the License.
     * You may obtain a copy of the License at
     *
     *     http://www.apache.org/licenses/LICENSE-2.0
     *
     * Unless required by applicable law or agreed to in writing, software
     * distributed under the License is distributed on an "AS IS" BASIS,
     * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
     * See the License for the specific language governing permissions and
     * limitations under the License.
     */
package org.rapla.plugin.appointmentnote;


import org.rapla.entities.IllegalAnnotationException;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.internal.EvalContext;
import org.rapla.entities.extensionpoints.Function;
import org.rapla.entities.extensionpoints.FunctionFactory;
import org.rapla.facade.RaplaFacade;
import org.rapla.inject.Extension;

import javax.inject.Inject;
import javax.inject.Provider;;
import java.util.List;


@Extension(provides = FunctionFactory.class, id= AppointmentNoteFunctions.NAMESPACE)
public class AppointmentNoteFunctions implements FunctionFactory {



    static final public String NAMESPACE = "appointment";

    Provider<RaplaFacade> facadeProvider;


    public @Inject AppointmentNoteFunctions(Provider<RaplaFacade> facadeProvider)
    {
        this.facadeProvider = facadeProvider;
    }

    public static void setNote(Appointment appointment, String comment) {
        try {
            Reservation event = appointment.getReservation();
            event.setAnnotation("appointment_note_" + appointment.getId(), comment);
        } catch (IllegalAnnotationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public static String getNote(Appointment appointment) {
        Reservation reservation = appointment.getReservation();
        if (reservation == null) {
            return null;
        }
        return reservation.getAnnotation("appointment_note_" + appointment.getId());
    }


@Override public Function createFunction(String functionName, List<Function> args) throws IllegalAnnotationException
    {
        if ( functionName.equals(AppointmentNoteFunction.name))
        {
            return new AppointmentNoteFunction(args);
        }
        return null;
    }

    private String getNote(Object obj)
    {
        final Appointment appointment;
        if ( obj instanceof AppointmentBlock)
        {
            appointment = ((AppointmentBlock)obj).getAppointment();
        }
        else if (obj instanceof Appointment) {
            appointment = (Appointment) obj;
        } else {
            appointment = null;
        }
        if ( appointment != null ) {
            String note = getNote(appointment);
            return note;
        }
        return null;
    }

    class AppointmentNoteFunction extends Function
    {
        static public final String name = "note";
        Function arg;

        public AppointmentNoteFunction(List<Function> args) throws IllegalAnnotationException
        {
            super(NAMESPACE,name, args);
            assertArgs(0,1);
            if ( args.size() > 0)
            {
                arg = args.get( 0);
            }

        }

        @Override public String eval(EvalContext context)
        {
            final Object obj;
            if ( arg != null)
            {
                obj = arg.eval( context);
            }
            else
            {
                obj = context.getFirstContextObject();
            }
            final String result = getNote(obj);
            return result;
        }

    }


}
