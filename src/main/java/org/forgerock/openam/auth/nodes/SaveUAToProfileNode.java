/*
 * simon.moffatt@forgerock.com
 *
 * Saves hash of requesting user agent to user profile
 *
 */

/*
 * Copyright 2017 ForgeRock AS. All Rights Reserved
 *
 * Use of this code requires a commercial software license with ForgeRock AS.
 * or with one of its affiliates. All use shall be exclusively subject
 * to such license between the licensee and ForgeRock AS.
 */

package org.forgerock.openam.auth.nodes;

import com.google.inject.assistedinject.Assisted;
import com.iplanet.sso.SSOException;
import com.sun.identity.idm.AMIdentity;
import com.sun.identity.idm.IdRepoException;
import com.sun.identity.shared.debug.Debug;
import org.forgerock.openam.annotations.sm.Attribute;
import org.forgerock.openam.auth.node.api.Action;
import org.forgerock.openam.auth.node.api.Node;
import org.forgerock.openam.auth.node.api.SingleOutcomeNode;
import org.forgerock.openam.auth.node.api.TreeContext;
import org.forgerock.openam.core.CoreWrapper;

import javax.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.forgerock.openam.auth.node.api.SharedStateConstants.REALM;
import static org.forgerock.openam.auth.node.api.SharedStateConstants.USERNAME;

/**
 * A node which captures and stores a SHA256 hash of the requesting IP address to the user profile
 */
@Node.Metadata(outcomeProvider = SingleOutcomeNode.OutcomeProvider.class,
        configClass = SaveUAToProfileNode.Config.class)
public class SaveUAToProfileNode extends SingleOutcomeNode {

    private final static String DEBUG_FILE = "SaveUAToProfileNode";
    protected Debug debug = Debug.getInstance(DEBUG_FILE);
    private final CoreWrapper coreWrapper;


    /**
     * Configuration for the node.
     */
    public interface Config {
        /**
         * A map of property name to value.
         * @return a map of properties.
         */
        @Attribute(order = 100)
        String attribute();

        //Toggle as to whether UA is stored in the clear or SHA256 hashed for privacy
        @Attribute(order = 200)
        default boolean storeAsHash() {
            return false;
        }

    }

    private final Config config;

    /**
     * Constructs a new SetSessionPropertiesNode instance.
     * @param config Node configuration.
     */
    @Inject
    public SaveUAToProfileNode(@Assisted Config config, CoreWrapper coreWrapper) {
        this.config = config;
        this.coreWrapper = coreWrapper;
    }

    @Override
    public Action process(TreeContext context) {

        //Pull out clientIP for the current request
        String clientUA = context.request.headers.get("User-Agent").toString();
        debug.message("[" + DEBUG_FILE + "]: client user agent found as :" + clientUA);

        if(config.storeAsHash()) {

            //Create SHA256 of user agent
            MessageDigest digest = null;
            try {
                digest = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            }
            byte[] clientUAPHash = digest.digest(clientUA.getBytes(StandardCharsets.UTF_8));
            StringBuffer hexString = new StringBuffer();

            for (int i = 0; i < clientUAPHash.length; i++) {
                String hex = Integer.toHexString(0xff & clientUAPHash[i]);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }

            String uaAsHash = hexString.toString();
            debug.message("[" + DEBUG_FILE + "]: hash of client user agent as : " + uaAsHash);

            //Wrapper to access profile
            AMIdentity userIdentity = coreWrapper.getIdentity(context.sharedState.get(USERNAME).asString(), context.sharedState.get(REALM).asString());


            //Create payload that will be saved to profile
            Map<String, Set> map = new HashMap<String, Set>();
            Set<String> values = new HashSet<String>();
            values.add(uaAsHash);
            map.put(config.attribute(), values);

            //Try and save against the user profile
            try {

                userIdentity.setAttributes(map);
                userIdentity.store();

            } catch (IdRepoException e) {

                debug.error("[" + DEBUG_FILE + "]: " + " Error storing profile attribute '{}' ", e);

            } catch (SSOException e) {

                debug.error("[" + DEBUG_FILE + "]: " + "Node exception", e);

            }

        } else {

            //Wrapper to access profile
            AMIdentity userIdentity = coreWrapper.getIdentity(context.sharedState.get(USERNAME).asString(), context.sharedState.get(REALM).asString());


            //Create payload that will be saved to profile
            Map<String, Set> map = new HashMap<String, Set>();
            Set<String> values = new HashSet<String>();
            values.add(clientUA);
            map.put(config.attribute(), values);

            //Try and save against the user profile
            try {

                userIdentity.setAttributes(map);
                userIdentity.store();

            } catch (IdRepoException e) {

                debug.error("[" + DEBUG_FILE + "]: " + " Error storing profile attribute '{}' ", e);

            } catch (SSOException e) {

                debug.error("[" + DEBUG_FILE + "]: " + "Node exception", e);

            }


        }



        return goToNext().build();
    }
}
