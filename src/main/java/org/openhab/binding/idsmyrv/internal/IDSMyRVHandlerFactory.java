package org.openhab.binding.idsmyrv.internal;

import static org.openhab.binding.idsmyrv.internal.IDSMyRVBindingConstants.*;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.idsmyrv.internal.handler.IDSMyRVBridgeHandler;
import org.openhab.binding.idsmyrv.internal.handler.LightHandler;
import org.openhab.binding.idsmyrv.internal.handler.RGBLightHandler;
import org.openhab.binding.idsmyrv.internal.handler.TankSensorHandler;
import org.openhab.binding.idsmyrv.internal.handler.LatchingRelayHandler;
import org.openhab.binding.idsmyrv.internal.handler.MomentaryHBridgeHandler;
import org.openhab.binding.idsmyrv.internal.handler.HVACHandler;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.binding.BaseThingHandlerFactory;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerFactory;
import org.osgi.service.component.annotations.Component;

/**
 * The {@link IDSMyRVHandlerFactory} is responsible for creating thing handlers.
 *
 * @author Simon Ulbrich - Initial contribution
 */
@NonNullByDefault
@Component(service = ThingHandlerFactory.class, immediate = true)
public class IDSMyRVHandlerFactory extends BaseThingHandlerFactory {

    private static final Set<ThingTypeUID> SUPPORTED_THING_TYPES_UIDS;

    static {
        // Initialize supported thing types in a way compatible with older Java versions
        Set<ThingTypeUID> types = new HashSet<>();
        types.add(THING_TYPE_BRIDGE);
        types.add(THING_TYPE_LIGHT);
        types.add(THING_TYPE_RGB_LIGHT);
        types.add(THING_TYPE_TANK_SENSOR);
        types.add(THING_TYPE_LATCHING_RELAY);
        types.add(THING_TYPE_MOMENTARY_H_BRIDGE);
        types.add(THING_TYPE_HVAC);
        SUPPORTED_THING_TYPES_UIDS = Collections.unmodifiableSet(types);
    }

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return SUPPORTED_THING_TYPES_UIDS.contains(thingTypeUID);
    }

    @Override
    protected @Nullable ThingHandler createHandler(Thing thing) {
        ThingTypeUID thingTypeUID = thing.getThingTypeUID();

        if (THING_TYPE_BRIDGE.equals(thingTypeUID)) {
            return new IDSMyRVBridgeHandler((Bridge) thing);
        } else if (THING_TYPE_LIGHT.equals(thingTypeUID)) {
            return new LightHandler(thing);
        } else if (THING_TYPE_RGB_LIGHT.equals(thingTypeUID)) {
            return new RGBLightHandler(thing);
        } else if (THING_TYPE_TANK_SENSOR.equals(thingTypeUID)) {
            return new TankSensorHandler(thing);
        } else if (THING_TYPE_LATCHING_RELAY.equals(thingTypeUID)) {
            return new LatchingRelayHandler(thing);
        } else if (THING_TYPE_MOMENTARY_H_BRIDGE.equals(thingTypeUID)) {
            return new MomentaryHBridgeHandler(thing);
        } else if (THING_TYPE_HVAC.equals(thingTypeUID)) {
            return new HVACHandler(thing);
        }

        return null;
    }
}
