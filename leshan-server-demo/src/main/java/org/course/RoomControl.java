/*
 *  Extension to leshan-server-demo for application code.
 */

package org.course;


import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.io.PrintWriter;

import org.eclipse.leshan.core.node.LwM2mSingleResource;
import org.eclipse.leshan.core.response.ResponseCallback;
import org.eclipse.leshan.server.californium.LeshanServer;
import org.eclipse.leshan.server.registration.Registration;
import org.eclipse.leshan.core.response.WriteResponse;
import org.eclipse.leshan.core.response.ReadResponse;
import org.eclipse.leshan.core.response.ObserveResponse;
import org.eclipse.leshan.core.request.WriteRequest;
import org.eclipse.leshan.core.request.ReadRequest;
import org.eclipse.leshan.core.request.ObserveRequest;
import org.eclipse.leshan.core.request.WriteRequest.Mode;
import org.eclipse.leshan.core.node.LwM2mResource;
import org.eclipse.leshan.core.node.LwM2mPath;
import org.eclipse.leshan.core.observation.SingleObservation;


public class RoomControl {

    //
    // Static reference to the server.
    //
    private static LeshanServer lwServer;
    private static Map<String, Integer> peakPowerMap = new HashMap<>();
    private static int maximumPeakRoomPower = 0;
    private static String roomName = "Living Room";  // arbitrary


    //
    // 2IMN15:  TODO  : fill in
    //
    // Declare variables to keep track of the state of the room.
    //

    public static void Initialize(LeshanServer server) {
        // Register the LWM2M server object for future use
        lwServer = server;

        // 2IMN15:  TODO  : fill in
        //
        // Initialize the state variables.

    }

    //
    // Suggested support methods:
    //
    // * set the dim level of all luminaires.
    // * set the power flag of all luminaires.
    // * show the status of the room.


    public static void handleRegistration(Registration registration) {
        // Check which objects are available.
        Map<Integer, org.eclipse.leshan.core.LwM2m.Version> supportedObject =
                registration.getSupportedObject();

        if (supportedObject.get(Constants.PRESENCE_DETECTOR_ID) != null) {
            System.out.println("Presence Detector:" + registration.getEndpoint());
            // 2IMN15:  Process the registration of a new Presence Detector.
            // 2IMN15:  The sequence diagram shows
            // 			Presence detector -> Room control (Register ep=IoT-Pi42 Presence)
            // 			Presence detector <- Room control (Created)
            // 			Presence detector <- Room control (GET presence observe)
            // 			Presence detector -> Room control (2.05 Content observe)

            // The first two already happens; so we have to do the GET presence observe;
            // So we subscribe to presence changes by observing the Presence resource
            ObserveRequest requestPresence = new ObserveRequest(Constants.PRESENCE_DETECTOR_ID, 0, Constants.RES_PRESENCE);

            try {
                // 2IMN15: We can now send the request we made, and wait 5 seconds to get a response
                ObserveResponse responsePresence = lwServer.send(registration, requestPresence, 5000);
                // 2IMN15: If we don't get a response within 5 seconds, or we get a non successful response; print about the failure
                if (responsePresence == null || !responsePresence.isSuccess()) {
                    System.out.println("Failed to start observing presence for " + registration.getEndpoint());
                } else {
                    // 2IMN15: Else print about the success
                    System.out.println("Started observing presence for " + registration.getEndpoint());
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        if (supportedObject.get(Constants.LUMINAIRE_ID) != null) {
            System.out.println("Luminaire");
            // 2IMN15:  Process the registration of a new Luminaire.
            // 2IMN15:  The sequence diagram shows
            // 			Luminaire -> Room control (Register ep=IoT-Pi42 Presence)
            // 			Luminaire <- Room control (Created)
            // 			Luminaire <- Room control (GET peak power Read)
            // 			Luminaire -> Room control (2.05 Peak Power)
            // 			Room control -> Room control (Add peak power to maximum room peak power)
            // 			Room control -> Room control (Add <ep, [Peak Power]> to peak power map)
            // The first two already happens; so we have to do the GET peak power Read;
            // So we can make a read peak power request that we can send similarly to the observe in the previous part
            ReadRequest readPeakPower = new ReadRequest(Constants.LUMINAIRE_ID, 0, Constants.RES_PEAK_POWER);

            try {
                ReadResponse resp = lwServer.send(registration, readPeakPower, 5000);
                if (resp == null || !resp.isSuccess()) {
                    System.out.println("Failed to read peak power for " + registration.getEndpoint());
                } else {
                    System.out.println("Read peak power for " + registration.getEndpoint());
                    int peakPower = readInteger(registration, Constants.LUMINAIRE_ID,0, Constants.RES_PEAK_POWER);
                    peakPowerMap.put(registration.getEndpoint(), peakPower);
                    maximumPeakRoomPower += peakPower;
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }


        }

        if (supportedObject.get(Constants.DEMAND_RESPONSE_ID) != null) {
            System.out.println("Demand Response");
            //
            // The registerDemandResponse() method contains example code
            // on how handle a registration.
            //
            int powerBudget = registerDemandResponse(registration);
        }

        //  2IMN15: don't forget to update the other luminaires.
    }


    public static void handleDeregistration(Registration registration) {
        // 2IMN15:  This also has a sequence diagram we can look at
        // The device identified by the given registration will disappear.  Update the state accordingly.
        // 			Luminaire -> Room control (Deregister ep=IoT-Pi43)
        // 			Room control -> Room control (Subtract peak power map (ep) from maximum room peak power)
        // 			Room control -> Room control (Remove peak power map (ep))
        // 			HalogenLuminaire -> Room control (Deregister ep=IoT-Pi44)
        // 			Room control -> Room control (Subtract peak power map (ep) from maximum room peak power)
        // 			Room control -> Room control (Remove peak power map (ep))

        // If the peak power map has the key then it's either a luminere or a haloge luminere so we can safely remove it
        if (peakPowerMap.containsKey(registration.getEndpoint())) {
            maximumPeakRoomPower -= peakPowerMap.get(registration.getEndpoint());
            peakPowerMap.remove(registration.getEndpoint());
        }

    }

    public static void handleObserveResponse(SingleObservation observation,
                                             Registration registration,
                                             ObserveResponse response) {
        if (registration != null && observation != null && response != null) {
            LwM2mPath observationPath = observation.getPath();

            // 2IMN15: Check if this observe comes from the PresenceDetector object
            if (observationPath.getObjectId() == Constants.PRESENCE_DETECTOR_ID &&
                    observationPath.getResourceId() == Constants.RES_PRESENCE) {
                // 2IMN15: Grab the presence value from the request
                boolean presence = (boolean) ((LwM2mResource) response.getContent()).getValue();
                // 2IMN15: Inform about the new presence value
                System.out.println("Presence update: " + presence);

                // We have to update every luminere in the peakpower map to the new presence value
                for (String endpoint : peakPowerMap.keySet()) {
                    Registration lumReg = lwServer.getRegistrationService().getByEndpoint(endpoint);
                    if (lumReg != null) {
                        // We need to send a request to set the RES_POWER to the same state as the presence (true/false)
                        WriteRequest setPowerReq = new WriteRequest(Constants.LUMINAIRE_ID, 0, Constants.RES_POWER, presence);
                        try {
                            WriteResponse wr = lwServer.send(lumReg, setPowerReq, 5000);
                            if (wr == null || !wr.isSuccess()) {
                                System.out.println("Failed to write power for " + endpoint);
                            }
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }

                    }
                }

            }


            // For processing an update of the Demand Response object.
            // It contains some example code.
            int newPowerBudget = observedDemandResponse(observation, response);
            if (newPowerBudget != -1) {
                // 2IMN15: Once again let's look at the sequence diagram
                // 			Demand Response -> Room control (Manually set total allowed peak room power)
                // 			Room control <- Room control (New dim level = 100 * total allowed / maximum room peak )
                // ALT: 	Room control <- Room control (New dim level = 100 if New dim level > 100 )
                //       	Room control -> ep for ep in powerMap (POST Dim Level New dim level)
                //       	Room control <- ep for ep in powerMap (204 changed)
                int dimLevel = Math.min(100, 100 * newPowerBudget / maximumPeakRoomPower); // handles both the calculation and alt at the same time
                for (String endpoint : peakPowerMap.keySet()) {
                    Registration lumReg = lwServer.getRegistrationService().getByEndpoint(endpoint);
                    if (lumReg != null) {
                        // We need to send a request to set the RES_DIM_LEVEL to the same state as the new dim value
                        WriteRequest setDim = new WriteRequest(Constants.LUMINAIRE_ID, 0, Constants.RES_DIM_LEVEL, dimLevel);
                        try {
                            WriteResponse wr = lwServer.send(lumReg, setDim, 5000);
                            if (wr == null || !wr.isSuccess()) {
                                System.out.println("Failed to write dim level for " + endpoint);
                            }
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }

                    }
                }
            }

        }
    }


    // Support functions for reading and writing resources of
    // certain types.

    // Returns the current power budget.
    private static int registerDemandResponse(Registration registration) {
        int powerBudget = readInteger(registration,
                Constants.DEMAND_RESPONSE_ID,
                0,
                Constants.RES_TOTAL_BUDGET);
        System.out.println("Power budget is " + powerBudget);
        // Observe the total budget information for updates.
        try {
            ObserveRequest obRequest =
                    new ObserveRequest(Constants.DEMAND_RESPONSE_ID,
                            0,
                            Constants.RES_TOTAL_BUDGET);
            System.out.println(">> ObserveRequest created << ");
            ObserveResponse coResponse =
                    lwServer.send(registration, obRequest, 1000);
            System.out.println(">> ObserveRequest sent << ");
            if (coResponse == null) {
                System.out.println(">>ObserveRequest null << ");
            }
        } catch (Exception e) {
            System.out.println("Observe request failed for Demand Response.");
        }
        return powerBudget;
    }

    // If the response contains a new power budget, it returns that value.
    // Otherwise, it returns -1.
    private static int observedDemandResponse(SingleObservation observation,
                                              ObserveResponse response) {
        // Alternative code:
        // String obsRes = observation.getPath().toString();
        // if (obsRes.equals("/33002/0/30005"))
        LwM2mPath obsPath = observation.getPath();
        if ((obsPath.getObjectId() == Constants.DEMAND_RESPONSE_ID) &&
                (obsPath.getResourceId() == Constants.RES_TOTAL_BUDGET)) {
            String strValue = ((LwM2mResource) response.getContent()).getValue().toString();
            try {
                int newPowerBudget = Integer.parseInt(strValue);

                return newPowerBudget;
            } catch (Exception e) {
                System.out.println("Exception in reading demand response:" + e.getMessage());
            }
        }
        return -1;
    }


    private static int readInteger(Registration registration, int objectId, int instanceId, int resourceId) {
        try {
            ReadRequest request = new ReadRequest(objectId, instanceId, resourceId);
            ReadResponse cResponse = lwServer.send(registration, request, 5000);
            if (cResponse.isSuccess()) {
                String sValue = ((LwM2mResource) cResponse.getContent()).getValue().toString();
                try {
                    int iValue = Integer.parseInt(((LwM2mResource) cResponse.getContent()).getValue().toString());
                    return iValue;
                } catch (Exception e) {
                }
                float fValue = Float.parseFloat(((LwM2mResource) cResponse.getContent()).getValue().toString());
                return (int) fValue;
            } else {
                return 0;
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
            System.out.println("readInteger: exception");
            return 0;
        }
    }

    private static String readString(Registration registration, int objectId, int instanceId, int resourceId) {
        try {
            ReadRequest request = new ReadRequest(objectId, instanceId, resourceId);
            ReadResponse cResponse = lwServer.send(registration, request, 1000);
            if (cResponse.isSuccess()) {
                String value = ((LwM2mResource) cResponse.getContent()).getValue().toString();
                return value;
            } else {
                return "";
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
            System.out.println("readString: exception");
            return "";
        }
    }

    private static void writeInteger(Registration registration, int objectId, int instanceId, int resourceId, int value) {
        try {
            WriteRequest request = new WriteRequest(objectId, instanceId, resourceId, value);
            WriteResponse cResponse = lwServer.send(registration, request, 1000);
            if (cResponse.isSuccess()) {
                System.out.println("writeInteger: Success");
            } else {
                System.out.println("writeInteger: Failed, " + cResponse.toString());
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
            System.out.println("writeInteger: exception");
        }
    }

    private static void writeString(Registration registration, int objectId, int instanceId, int resourceId, String value) {
        try {
            WriteRequest request = new WriteRequest(objectId, instanceId, resourceId, value);
            WriteResponse cResponse = lwServer.send(registration, request, 1000);
            if (cResponse.isSuccess()) {
                System.out.println("writeString: Success");
            } else {
                System.out.println("writeString: Failed, " + cResponse.toString());
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
            System.out.println("writeString: exception");
        }
    }

    private static void writeBoolean(Registration registration, int objectId, int instanceId, int resourceId, boolean value) {
        try {
            WriteRequest request = new WriteRequest(objectId, instanceId, resourceId, value);
            WriteResponse cResponse = lwServer.send(registration, request, 1000);
            if (cResponse.isSuccess()) {
                System.out.println("writeBoolean: Success");
            } else {
                System.out.println("writeBoolean: Failed, " + cResponse.toString());
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
            System.out.println("writeBoolean: exception");
        }
    }

}
