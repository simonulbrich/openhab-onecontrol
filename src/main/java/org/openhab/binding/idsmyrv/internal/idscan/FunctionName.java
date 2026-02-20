package org.openhab.binding.idsmyrv.internal.idscan;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * IDS-CAN function names.
 *
 * Function names provide human-readable names for devices based on their function
 * (e.g., "Kitchen Ceiling Light", "Front Bedroom Ceiling Light").
 *
 * @author Simon Ulbrich - Initial contribution
 */
@NonNullByDefault
public class FunctionName {
    private static final Map<Integer, FunctionName> LOOKUP = new HashMap<>();

    // Common function names (based on IDS-CAN protocol)
    public static final FunctionName UNKNOWN = new FunctionName(0, "Unknown");
    public static final FunctionName DIAGNOSTIC_TOOL = new FunctionName(1, "Diagnostic Tool");
    public static final FunctionName MYRV_TABLET = new FunctionName(2, "MyRV Tablet");
    public static final FunctionName GAS_WATER_HEATER = new FunctionName(3, "Gas Water Heater");
    public static final FunctionName ELECTRIC_WATER_HEATER = new FunctionName(4, "Electric Water Heater");
    public static final FunctionName WATER_PUMP = new FunctionName(5, "Water Pump");
    public static final FunctionName BATH_VENT = new FunctionName(6, "Bath Vent");
    public static final FunctionName LIGHT = new FunctionName(7, "Light");
    public static final FunctionName FLOOD_LIGHT = new FunctionName(8, "Flood Light");
    public static final FunctionName WORK_LIGHT = new FunctionName(9, "Work Light");
    public static final FunctionName FRONT_BEDROOM_CEILING_LIGHT = new FunctionName(10, "Front Bedroom Ceiling Light");
    public static final FunctionName FRONT_BEDROOM_OVERHEAD_LIGHT = new FunctionName(11,
            "Front Bedroom Overhead Light");
    public static final FunctionName FRONT_BEDROOM_VANITY_LIGHT = new FunctionName(12, "Front Bedroom Vanity Light");
    public static final FunctionName FRONT_BEDROOM_SCONCE_LIGHT = new FunctionName(13, "Front Bedroom Sconce Light");
    public static final FunctionName FRONT_BEDROOM_LOFT_LIGHT = new FunctionName(14, "Front Bedroom Loft Light");
    public static final FunctionName REAR_BEDROOM_CEILING_LIGHT = new FunctionName(15, "Rear Bedroom Ceiling Light");
    public static final FunctionName REAR_BEDROOM_OVERHEAD_LIGHT = new FunctionName(16, "Rear Bedroom Overhead Light");
    public static final FunctionName REAR_BEDROOM_VANITY_LIGHT = new FunctionName(17, "Rear Bedroom Vanity Light");
    public static final FunctionName REAR_BEDROOM_SCONCE_LIGHT = new FunctionName(18, "Rear Bedroom Sconce Light");
    public static final FunctionName REAR_BEDROOM_LOFT_LIGHT = new FunctionName(19, "Rear Bedroom Loft Light");
    public static final FunctionName LOFT_LIGHT = new FunctionName(20, "Loft Light");
    public static final FunctionName FRONT_HALL_LIGHT = new FunctionName(21, "Front Hall Light");
    public static final FunctionName REAR_HALL_LIGHT = new FunctionName(22, "Rear Hall Light");
    public static final FunctionName FRONT_BATHROOM_LIGHT = new FunctionName(23, "Front Bathroom Light");
    public static final FunctionName FRONT_BATHROOM_VANITY_LIGHT = new FunctionName(24, "Front Bathroom Vanity Light");
    public static final FunctionName FRONT_BATHROOM_CEILING_LIGHT = new FunctionName(25,
            "Front Bathroom Ceiling Light");
    public static final FunctionName FRONT_BATHROOM_SHOWER_LIGHT = new FunctionName(26, "Front Bathroom Shower Light");
    public static final FunctionName FRONT_BATHROOM_SCONCE_LIGHT = new FunctionName(27, "Front Bathroom Sconce Light");
    public static final FunctionName REAR_BATHROOM_VANITY_LIGHT = new FunctionName(28, "Rear Bathroom Vanity Light");
    public static final FunctionName REAR_BATHROOM_CEILING_LIGHT = new FunctionName(29, "Rear Bathroom Ceiling Light");
    public static final FunctionName REAR_BATHROOM_SHOWER_LIGHT = new FunctionName(30, "Rear Bathroom Shower Light");
    public static final FunctionName REAR_BATHROOM_SCONCE_LIGHT = new FunctionName(31, "Rear Bathroom Sconce Light");
    public static final FunctionName KITCHEN_CEILING_LIGHT = new FunctionName(32, "Kitchen Ceiling Light");
    public static final FunctionName KITCHEN_SCONCE_LIGHT = new FunctionName(33, "Kitchen Sconce Light");
    public static final FunctionName KITCHEN_PENDANTS_LIGHT = new FunctionName(34, "Kitchen Pendants Light");
    public static final FunctionName KITCHEN_RANGE_LIGHT = new FunctionName(35, "Kitchen Range Light");
    public static final FunctionName KITCHEN_COUNTER_LIGHT = new FunctionName(36, "Kitchen Counter Light");
    public static final FunctionName KITCHEN_BAR_LIGHT = new FunctionName(37, "Kitchen Bar Light");
    public static final FunctionName KITCHEN_ISLAND_LIGHT = new FunctionName(38, "Kitchen Island Light");
    public static final FunctionName KITCHEN_CHANDELIER_LIGHT = new FunctionName(39, "Kitchen Chandelier Light");
    public static final FunctionName KITCHEN_UNDER_CABINET_LIGHT = new FunctionName(40, "Kitchen Under Cabinet Light");
    public static final FunctionName LIVING_ROOM_CEILING_LIGHT = new FunctionName(41, "Living Room Ceiling Light");
    public static final FunctionName LIVING_ROOM_SCONCE_LIGHT = new FunctionName(42, "Living Room Sconce Light");
    public static final FunctionName LIVING_ROOM_PENDANTS_LIGHT = new FunctionName(43, "Living Room Pendants Light");
    public static final FunctionName LIVING_ROOM_BAR_LIGHT = new FunctionName(44, "Living Room Bar Light");
    public static final FunctionName GARAGE_CEILING_LIGHT = new FunctionName(45, "Garage Ceiling Light");
    public static final FunctionName GARAGE_CABINET_LIGHT = new FunctionName(46, "Garage Cabinet Light");
    public static final FunctionName SECURITY_LIGHT = new FunctionName(47, "Security Light");
    public static final FunctionName PORCH_LIGHT = new FunctionName(48, "Porch Light");
    public static final FunctionName AWNING_LIGHT = new FunctionName(49, "Awning Light");
    public static final FunctionName BATHROOM_LIGHT = new FunctionName(50, "Bathroom Light");
    public static final FunctionName BATHROOM_VANITY_LIGHT = new FunctionName(51, "Bathroom Vanity Light");
    public static final FunctionName BATHROOM_CEILING_LIGHT = new FunctionName(52, "Bathroom Ceiling Light");
    public static final FunctionName BATHROOM_SHOWER_LIGHT = new FunctionName(53, "Bathroom Shower Light");
    public static final FunctionName BATHROOM_SCONCE_LIGHT = new FunctionName(54, "Bathroom Sconce Light");
    public static final FunctionName HALL_LIGHT = new FunctionName(55, "Hall Light");
    public static final FunctionName BUNK_ROOM_LIGHT = new FunctionName(56, "Bunk Room Light");
    public static final FunctionName BEDROOM_LIGHT = new FunctionName(57, "Bedroom Light");
    public static final FunctionName LIVING_ROOM_LIGHT = new FunctionName(58, "Living Room Light");
    public static final FunctionName KITCHEN_LIGHT = new FunctionName(59, "Kitchen Light");
    public static final FunctionName LOUNGE_LIGHT = new FunctionName(60, "Lounge Light");
    public static final FunctionName CEILING_LIGHT = new FunctionName(61, "Ceiling Light");
    public static final FunctionName ENTRY_LIGHT = new FunctionName(62, "Entry Light");
    public static final FunctionName BED_CEILING_LIGHT = new FunctionName(63, "Bed Ceiling Light");
    public static final FunctionName BEDROOM_LAV_LIGHT = new FunctionName(64, "Bedroom Lav Light");
    public static final FunctionName SHOWER_LIGHT = new FunctionName(65, "Shower Light");
    public static final FunctionName GALLEY_LIGHT = new FunctionName(66, "Galley Light");
    public static final FunctionName FRESH_TANK = new FunctionName(67, "Fresh Tank");
    public static final FunctionName GREY_TANK = new FunctionName(68, "Grey Tank");
    public static final FunctionName BLACK_TANK = new FunctionName(69, "Black Tank");
    public static final FunctionName FUEL_TANK = new FunctionName(70, "Fuel Tank");
    public static final FunctionName GENERATOR_FUEL_TANK = new FunctionName(71, "Generator Fuel Tank");
    public static final FunctionName AUXILLIARY_FUEL_TANK = new FunctionName(72, "Auxilliary Fuel Tank");
    public static final FunctionName FRONT_BATH_GREY_TANK = new FunctionName(73, "Front Bath Grey Tank");
    public static final FunctionName FRONT_BATH_FRESH_TANK = new FunctionName(74, "Front Bath Fresh Tank");
    public static final FunctionName FRONT_BATH_BLACK_TANK = new FunctionName(75, "Front Bath Black Tank");
    public static final FunctionName REAR_BATH_GREY_TANK = new FunctionName(76, "Rear Bath Grey Tank");
    public static final FunctionName REAR_BATH_FRESH_TANK = new FunctionName(77, "Rear Bath Fresh Tank");
    public static final FunctionName REAR_BATH_BLACK_TANK = new FunctionName(78, "Rear Bath Black Tank");
    public static final FunctionName MAIN_BATH_GREY_TANK = new FunctionName(79, "Main Bath Grey Tank");
    public static final FunctionName MAIN_BATH_FRESH_TANK = new FunctionName(80, "Main Bath Fresh Tank");
    public static final FunctionName MAIN_BATH_BLACK_TANK = new FunctionName(81, "Main Bath Black Tank");
    public static final FunctionName GALLEY_GREY_TANK = new FunctionName(82, "Galley Grey Tank");
    public static final FunctionName GALLEY_FRESH_TANK = new FunctionName(83, "Galley Fresh Tank");
    public static final FunctionName GALLEY_BLACK_TANK = new FunctionName(84, "Galley Black Tank");
    public static final FunctionName KITCHEN_GREY_TANK = new FunctionName(85, "Kitchen Grey Tank");
    public static final FunctionName KITCHEN_FRESH_TANK = new FunctionName(86, "Kitchen Fresh Tank");
    public static final FunctionName KITCHEN_BLACK_TANK = new FunctionName(87, "Kitchen Black Tank");
    public static final FunctionName LANDING_GEAR = new FunctionName(88, "Landing Gear");
    public static final FunctionName FRONT_STABILIZER = new FunctionName(89, "Front Stabilizer");
    public static final FunctionName REAR_STABILIZER = new FunctionName(90, "Rear Stabilizer");
    public static final FunctionName TV_LIFT = new FunctionName(91, "TV Lift");
    public static final FunctionName BED_LIFT = new FunctionName(92, "Bed Lift");
    public static final FunctionName BATH_VENT_COVER = new FunctionName(93, "Bath Vent Cover");
    public static final FunctionName DOOR_LOCK = new FunctionName(94, "Door Lock");
    public static final FunctionName GENERATOR = new FunctionName(95, "Generator");
    public static final FunctionName SLIDE = new FunctionName(96, "Slide");
    public static final FunctionName MAIN_SLIDE = new FunctionName(97, "Main Slide");
    public static final FunctionName BEDROOM_SLIDE = new FunctionName(98, "Bedroom Slide");
    public static final FunctionName GALLEY_SLIDE = new FunctionName(99, "Galley Slide");
    public static final FunctionName KITCHEN_SLIDE = new FunctionName(100, "Kitchen Slide");
    public static final FunctionName CLOSET_SLIDE = new FunctionName(101, "Closet Slide");
    public static final FunctionName OPTIONAL_SLIDE = new FunctionName(102, "Optional Slide");
    public static final FunctionName DOOR_SIDE_SLIDE = new FunctionName(103, "Door Side Slide");
    public static final FunctionName OFF_DOOR_SLIDE = new FunctionName(104, "Off-Door Slide");
    public static final FunctionName AWNING = new FunctionName(105, "Awning");
    public static final FunctionName LEVEL_UP_LEVELER = new FunctionName(106, "Level Up Leveler");
    public static final FunctionName WATER_TANK_HEATER = new FunctionName(107, "Water Tank Heater");
    public static final FunctionName MYRV_TOUCHSCREEN = new FunctionName(108, "MyRV Touchscreen");
    public static final FunctionName LEVELER = new FunctionName(109, "Leveler");
    public static final FunctionName VENT_COVER = new FunctionName(110, "Vent Cover");
    public static final FunctionName FRONT_BEDROOM_VENT_COVER = new FunctionName(111, "Front Bedroom Vent Cover");
    public static final FunctionName BEDROOM_VENT_COVER = new FunctionName(112, "Bedroom Vent Cover");
    public static final FunctionName FRONT_BATHROOM_VENT_COVER = new FunctionName(113, "Front Bath Vent Cover");
    public static final FunctionName MAIN_BATHROOM_VENT_COVER = new FunctionName(114, "Main Bath Vent Cover");
    public static final FunctionName REAR_BATHROOM_VENT_COVER = new FunctionName(115, "Rear Bath Vent Cover");
    public static final FunctionName KITCHEN_VENT_COVER = new FunctionName(116, "Kitchen Vent Cover");
    public static final FunctionName LIVING_ROOM_VENT_COVER = new FunctionName(117, "Living Room Vent Cover");
    public static final FunctionName FOUR_LEG_TRUCK_CAMPLER_LEVELER = new FunctionName(118,
            "4-Leg Truck Camper Leveler");
    public static final FunctionName SIX_LEG_HALL_EFFECT_EJ_LEVELER = new FunctionName(119,
            "6-Leg Hall Effect EJ Leveler");
    public static final FunctionName PATIO_LIGHT = new FunctionName(120, "Patio Light");
    public static final FunctionName HUTCH_LIGHT = new FunctionName(121, "Hutch Light");
    public static final FunctionName SCARE_LIGHT = new FunctionName(122, "Scare Light");
    public static final FunctionName DINETTE_LIGHT = new FunctionName(123, "Dinette Light");
    public static final FunctionName BAR_LIGHT = new FunctionName(124, "Bar Light");
    public static final FunctionName OVERHEAD_LIGHT = new FunctionName(125, "Overhead Light");
    public static final FunctionName OVERHEAD_BAR_LIGHT = new FunctionName(126, "Overhead Bar Light");
    public static final FunctionName FOYER_LIGHT = new FunctionName(127, "Foyer Light");
    public static final FunctionName RAMP_DOOR_LIGHT = new FunctionName(128, "Ramp Door Light");
    public static final FunctionName ENTERTAINMENT_LIGHT = new FunctionName(129, "Entertainment Light");
    public static final FunctionName REAR_ENTRY_DOOR_LIGHT = new FunctionName(130, "Rear Entry Door Light");
    public static final FunctionName CEILING_FAN_LIGHT = new FunctionName(131, "Ceiling Fan Light");
    public static final FunctionName OVERHEAD_FAN_LIGHT = new FunctionName(132, "Overhead Fan Light");
    public static final FunctionName BUNK_SLIDE = new FunctionName(133, "Bunk Slide");
    public static final FunctionName BED_SLIDE = new FunctionName(134, "Bed Slide");
    public static final FunctionName WARDROBE_SLIDE = new FunctionName(135, "Wardrobe Slide");
    public static final FunctionName ENTERTAINMENT_SLIDE = new FunctionName(136, "Entertainment Slide");
    public static final FunctionName SOFA_SLIDE = new FunctionName(137, "Sofa Slide");
    public static final FunctionName PATIO_AWNING = new FunctionName(138, "Patio Awning");
    public static final FunctionName REAR_AWNING = new FunctionName(139, "Rear Awning");
    public static final FunctionName SIDE_AWNING = new FunctionName(140, "Side Awning");
    public static final FunctionName JACKS = new FunctionName(141, "Jacks");
    public static final FunctionName LEVELER_2 = new FunctionName(142, "Leveler");
    public static final FunctionName EXTERIOR_LIGHT = new FunctionName(143, "Exterior Light");
    public static final FunctionName LOWER_ACCENT_LIGHT = new FunctionName(144, "Lower Accent Light");
    public static final FunctionName UPPER_ACCENT_LIGHT = new FunctionName(145, "Upper Accent Light");
    public static final FunctionName DS_SECURITY_LIGHT = new FunctionName(146, "DS Security Light");
    public static final FunctionName ODS_SECURITY_LIGHT = new FunctionName(147, "ODS Security Light");
    public static final FunctionName SLIDE_IN_SLIDE = new FunctionName(148, "Slide In Slide");
    public static final FunctionName HITCH_LIGHT = new FunctionName(149, "Hitch Light");
    public static final FunctionName CLOCK = new FunctionName(150, "Clock");
    public static final FunctionName TV = new FunctionName(151, "TV");
    public static final FunctionName DVD = new FunctionName(152, "DVD");
    public static final FunctionName BLU_RAY = new FunctionName(153, "Blu-ray");
    public static final FunctionName VCR = new FunctionName(154, "VCR");
    public static final FunctionName PVR = new FunctionName(155, "PVR");
    public static final FunctionName CABLE = new FunctionName(156, "Cable");
    public static final FunctionName SATELLITE = new FunctionName(157, "Satellite");
    public static final FunctionName AUDIO = new FunctionName(158, "Audio");
    public static final FunctionName CD_PLAYER = new FunctionName(159, "CD Player");
    public static final FunctionName TUNER = new FunctionName(160, "Tuner");
    public static final FunctionName RADIO = new FunctionName(161, "Radio");
    public static final FunctionName SPEAKERS = new FunctionName(162, "Speakers");
    public static final FunctionName GAME = new FunctionName(163, "Game");
    public static final FunctionName CLOCK_RADIO = new FunctionName(164, "Clock Radio");
    public static final FunctionName AUX = new FunctionName(165, "Aux");
    public static final FunctionName CLIMATE_ZONE = new FunctionName(166, "Climate zone");
    public static final FunctionName FIREPLACE = new FunctionName(167, "Fireplace");
    public static final FunctionName THERMOSTAT = new FunctionName(168, "Thermostat");
    public static final FunctionName FRONT_CAP_LIGHT = new FunctionName(169, "Front Cap Light");
    public static final FunctionName STEP_LIGHT = new FunctionName(170, "Step Light");
    public static final FunctionName DS_FLOOD_LIGHT = new FunctionName(171, "DS Flood Light");
    public static final FunctionName INTERIOR_LIGHT = new FunctionName(172, "Interior Light");
    public static final FunctionName FRESH_TANK_HEATER = new FunctionName(173, "Fresh Tank Heater");
    public static final FunctionName GREY_TANK_HEATER = new FunctionName(174, "Grey Tank Heater");
    public static final FunctionName BLACK_TANK_HEATER = new FunctionName(175, "Black Tank Heater");
    public static final FunctionName LP_TANK = new FunctionName(176, "LP Tank");
    public static final FunctionName STALL_LIGHT = new FunctionName(177, "Stall Light");
    public static final FunctionName MAIN_LIGHT = new FunctionName(178, "Main Light");
    public static final FunctionName BATH_LIGHT = new FunctionName(179, "Bath Light");
    public static final FunctionName BUNK_LIGHT = new FunctionName(180, "Bunk Light");
    public static final FunctionName BED_LIGHT = new FunctionName(181, "Bed Light");
    public static final FunctionName CABINET_LIGHT = new FunctionName(182, "Cabinet Light");
    public static final FunctionName NETWORK_BRIDGE = new FunctionName(183, "Network Bridge");
    public static final FunctionName ETHERNET_BRIDGE = new FunctionName(184, "Ethernet Bridge");
    public static final FunctionName WIFI_BRIDGE = new FunctionName(185, "WiFi Bridge");
    public static final FunctionName IN_TRANSIT_POWER_DISCONNECT = new FunctionName(186, "In Transit Power Disconnect");
    public static final FunctionName LEVEL_UP_UNITY = new FunctionName(187, "Level Up Unity");
    public static final FunctionName TT_LEVELER = new FunctionName(188, "TT Leveler");
    public static final FunctionName TRAVEL_TRAILER_LEVELER = new FunctionName(189, "Travel Trailer Leveler");
    public static final FunctionName FIFTH_WHEEL_LEVELER = new FunctionName(190, "Fifth Wheel Leveler");
    public static final FunctionName FUEL_PUMP = new FunctionName(191, "Fuel Pump");
    public static final FunctionName MAIN_CLIMATE_ZONE = new FunctionName(192, "Main Climate Zone");
    public static final FunctionName BEDROOM_CLIMATE_ZONE = new FunctionName(193, "Bedroom Climate Zone");
    public static final FunctionName GARAGE_CLIMATE_ZONE = new FunctionName(194, "Garage Climate Zone");
    public static final FunctionName COMPARTMENT_LIGHT = new FunctionName(195, "Compartment Light");
    public static final FunctionName TRUNK_LIGHT = new FunctionName(196, "Trunk Light");
    public static final FunctionName BAR_TV = new FunctionName(197, "Bar TV");
    public static final FunctionName BATHROOM_TV = new FunctionName(198, "Bathroom TV");
    public static final FunctionName BEDROOM_TV = new FunctionName(199, "Bedroom TV");
    public static final FunctionName BUNK_ROOM_TV = new FunctionName(200, "Bunk Room TV");
    public static final FunctionName EXTERIOR_TV = new FunctionName(201, "Exterior TV");
    public static final FunctionName FRONT_BATHROOM_TV = new FunctionName(202, "Front Bathroom TV");
    public static final FunctionName FRONT_BEDROOM_TV = new FunctionName(203, "Front Bedroom TV");
    public static final FunctionName GARAGE_TV = new FunctionName(204, "Garage TV");
    public static final FunctionName KITCHEN_TV = new FunctionName(205, "Kitchen TV");
    public static final FunctionName LIVING_ROOM_TV = new FunctionName(206, "Living Room TV");
    public static final FunctionName LOFT_TV = new FunctionName(207, "Loft TV");
    public static final FunctionName LOUNGE_TV = new FunctionName(208, "Lounge TV");
    public static final FunctionName MAIN_TV = new FunctionName(209, "Main TV");
    public static final FunctionName PATIO_TV = new FunctionName(210, "Patio TV");
    public static final FunctionName REAR_BATHROOM_TV = new FunctionName(211, "Rear Bathroom TV");
    public static final FunctionName REAR_BEDROOM_TV = new FunctionName(212, "Rear Bedroom TV");
    public static final FunctionName BATHROOM_DOOR_LOCK = new FunctionName(213, "Bathroom Door Lock");
    public static final FunctionName BEDROOM_DOOR_LOCK = new FunctionName(214, "Bedroom Door Lock");
    public static final FunctionName FRONT_DOOR_LOCK = new FunctionName(215, "Front Door Lock");
    public static final FunctionName GARAGE_DOOR_LOCK = new FunctionName(216, "Garage Door Lock");
    public static final FunctionName MAIN_DOOR_LOCK = new FunctionName(217, "Main Door Lock");
    public static final FunctionName PATIO_DOOR_LOCK = new FunctionName(218, "Patio Door Lock");
    public static final FunctionName REAR_DOOR_LOCK = new FunctionName(219, "Rear Door Lock");
    public static final FunctionName ACCENT_LIGHT = new FunctionName(220, "Accent Light");
    public static final FunctionName BATHROOM_ACCENT_LIGHT = new FunctionName(221, "Bathroom Accent Light");
    public static final FunctionName BEDROOM_ACCENT_LIGHT = new FunctionName(222, "Bedroom Accent Light");
    public static final FunctionName FRONT_BEDROOM_ACCENT_LIGHT = new FunctionName(223, "Front Bedroom Accent Light");
    public static final FunctionName GARAGE_ACCENT_LIGHT = new FunctionName(224, "Garage Accent Light");
    public static final FunctionName KITCHEN_ACCENT_LIGHT = new FunctionName(225, "Kitchen Accent Light");
    public static final FunctionName PATIO_ACCENT_LIGHT = new FunctionName(226, "Patio Accent Light");
    public static final FunctionName REAR_BEDROOM_ACCENT_LIGHT = new FunctionName(227, "Rear Bedroom Accent Light");
    public static final FunctionName BEDROOM_RADIO = new FunctionName(228, "Bedroom Radio");
    public static final FunctionName BUNK_ROOM_RADIO = new FunctionName(229, "Bunk Room Radio");
    public static final FunctionName EXTERIOR_RADIO = new FunctionName(230, "Exterior Radio");
    public static final FunctionName FRONT_BEDROOM_RADIO = new FunctionName(231, "Front Bedroom Radio");
    public static final FunctionName GARAGE_RADIO = new FunctionName(232, "Garage Radio");
    public static final FunctionName KITCHEN_RADIO = new FunctionName(233, "Kitchen Radio");
    public static final FunctionName LIVING_ROOM_RADIO = new FunctionName(234, "Living Room Radio");
    public static final FunctionName LOFT_RADIO = new FunctionName(235, "Loft Radio");
    public static final FunctionName PATIO_RADIO = new FunctionName(236, "Patio Radio");
    public static final FunctionName REAR_BEDROOM_RADIO = new FunctionName(237, "Rear Bedroom Radio");
    public static final FunctionName BEDROOM_ENTERTAINMENT_SYSTEM = new FunctionName(238,
            "Bedroom Entertainment System");
    public static final FunctionName BUNK_ROOM_ENTERTAINMENT_SYSTEM = new FunctionName(239,
            "Bunk Room Entertainment System");
    public static final FunctionName ENTERTAINMENT_SYSTEM = new FunctionName(240, "Entertainment System");
    public static final FunctionName EXTERIOR_ENTERTAINMENT_SYSTEM = new FunctionName(241,
            "Exterior Entertainment System");
    public static final FunctionName FRONT_BEDROOM_ENTERTAINMENT_SYSTEM = new FunctionName(242,
            "Front Bedroom Entertainment System");
    public static final FunctionName GARAGE_ENTERTAINMENT_SYSTEM = new FunctionName(243, "Garage Entertainment System");
    public static final FunctionName KITCHEN_ENTERTAINMENT_SYSTEM = new FunctionName(244,
            "Kitchen Entertainment System");
    public static final FunctionName LIVING_ROOM_ENTERTAINMENT_SYSTEM = new FunctionName(245,
            "Living Room Entertainment System");
    public static final FunctionName LOFT_ENTERTAINMENT_SYSTEM = new FunctionName(246, "Loft Entertainment System");
    public static final FunctionName MAIN_ENTERTAINMENT_SYSTEM = new FunctionName(247, "Main Entertainment System");
    public static final FunctionName PATIO_ENTERTAINMENT_SYSTEM = new FunctionName(248, "Patio Entertainment System");
    public static final FunctionName REAR_BEDROOM_ENTERTAINMENT_SYSTEM = new FunctionName(249,
            "Rear Bedroom Entertainment System");
    public static final FunctionName LEFT_STABILIZER = new FunctionName(250, "Left Stabilizer");
    public static final FunctionName RIGHT_STABILIZER = new FunctionName(251, "Right Stabilizer");
    public static final FunctionName STABILIZER = new FunctionName(252, "Stabilizer");
    public static final FunctionName SOLAR = new FunctionName(253, "Solar");
    public static final FunctionName SOLAR_POWER = new FunctionName(254, "Solar Power");
    public static final FunctionName BATTERY = new FunctionName(255, "Battery");
    public static final FunctionName MAIN_BATTERY = new FunctionName(256, "Main Battery");
    public static final FunctionName AUX_BATTERY = new FunctionName(257, "Aux Battery");
    public static final FunctionName SHORE_POWER = new FunctionName(258, "Shore Power");
    public static final FunctionName AC_POWER = new FunctionName(259, "AC Power");
    public static final FunctionName AC_MAINS = new FunctionName(260, "AC Mains");
    public static final FunctionName AUX_POWER = new FunctionName(261, "Aux Power");
    public static final FunctionName OUTPUTS = new FunctionName(262, "Outputs");
    public static final FunctionName RAMP_DOOR = new FunctionName(263, "Ramp Door");
    public static final FunctionName FAN = new FunctionName(264, "Fan");
    public static final FunctionName BATH_FAN = new FunctionName(265, "Bath Fan");
    public static final FunctionName REAR_FAN = new FunctionName(266, "Rear Fan");
    public static final FunctionName FRONT_FAN = new FunctionName(267, "Front Fan");
    public static final FunctionName KITCHEN_FAN = new FunctionName(268, "Kitchen Fan");
    public static final FunctionName CEILING_FAN = new FunctionName(269, "Ceiling Fan");
    public static final FunctionName TANK_HEATER = new FunctionName(270, "Tank Heater");
    public static final FunctionName FRONT_CEILING_LIGHT = new FunctionName(271, "Front Ceiling Light");
    public static final FunctionName REAR_CEILING_LIGHT = new FunctionName(272, "Rear Ceiling Light");
    public static final FunctionName CARGO_LIGHT = new FunctionName(273, "Cargo Light");
    public static final FunctionName FASCIA_LIGHT = new FunctionName(274, "Fascia Light");
    public static final FunctionName SLIDE_CEILING_LIGHT = new FunctionName(275, "Slide Ceiling Light");
    public static final FunctionName SLIDE_OVERHEAD_LIGHT = new FunctionName(276, "Slide Overhead Light");
    public static final FunctionName DECOR_LIGHT = new FunctionName(277, "Décor Light");
    public static final FunctionName READING_LIGHT = new FunctionName(278, "Reading Light");
    public static final FunctionName FRONT_READING_LIGHT = new FunctionName(279, "Front Reading Light");
    public static final FunctionName REAR_READING_LIGHT = new FunctionName(280, "Rear Reading Light");
    public static final FunctionName LIVING_ROOM_CLIMATE_ZONE = new FunctionName(281, "Living Room Climate Zone");
    public static final FunctionName FRONT_LIVING_ROOM_CLIMATE_ZONE = new FunctionName(282,
            "Front Living Room Climate Zone");
    public static final FunctionName REAR_LIVING_ROOM_CLIMATE_ZONE = new FunctionName(283,
            "Rear Living Room Climate Zone");
    public static final FunctionName FRONT_BEDROOM_CLIMATE_ZONE = new FunctionName(284, "Front Bedroom Climate Zone");
    public static final FunctionName REAR_BEDROOM_CLIMATE_ZONE = new FunctionName(285, "Rear Bedroom Climate Zone");
    public static final FunctionName BED_TILT = new FunctionName(286, "Bed Tilt");
    public static final FunctionName FRONT_BED_TILT = new FunctionName(287, "Front Bed Tilt");
    public static final FunctionName REAR_BED_TILT = new FunctionName(288, "Rear Bed Tilt");
    public static final FunctionName MEN_LIGHT = new FunctionName(289, "Men's Light");
    public static final FunctionName WOMEN_LIGHT = new FunctionName(290, "Women's Light");
    public static final FunctionName SERVICE_LIGHT = new FunctionName(291, "Service Light");
    public static final FunctionName ODS_FLOOD_LIGHT = new FunctionName(292, "ODS Flood Light");
    public static final FunctionName UNDERBODY_ACCENT_LIGHT = new FunctionName(293, "Underbody Accent Light");
    public static final FunctionName SPEAKER_LIGHT = new FunctionName(294, "Speaker Light");

    private final int value;
    private final String name;

    private FunctionName(int value, String name) {
        this.value = value;
        this.name = name;
        if (value > 0) {
            LOOKUP.put(value, this);
        }
    }

    /**
     * Get the numeric value of this function name.
     *
     * @return The function name value
     */
    public int getValue() {
        return value;
    }

    /**
     * Get the human-readable name of this function.
     *
     * @return The function name
     */
    public String getName() {
        return name;
    }

    /**
     * Get a FunctionName by its numeric value.
     *
     * @param value The function name value (uint16)
     * @return The FunctionName, or UNKNOWN if not recognized
     */
    public static FunctionName fromValue(int value) {
        if (value == 0) {
            return UNKNOWN;
        }
        FunctionName fn = LOOKUP.get(value);
        if (fn != null) {
            return fn;
        }
        // Return a generic unknown with the value
        return new FunctionName(value, String.format("Unknown (0x%04X)", value));
    }

    @Override
    public String toString() {
        return String.format("%s(0x%04X)", name, value);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        FunctionName that = (FunctionName) obj;
        return value == that.value;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(value);
    }
}
