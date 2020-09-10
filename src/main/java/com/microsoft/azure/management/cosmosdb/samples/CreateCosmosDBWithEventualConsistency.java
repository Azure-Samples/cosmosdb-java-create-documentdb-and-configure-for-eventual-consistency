/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.management.cosmosdb.samples;

import com.azure.cosmos.ConsistencyLevel;
import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.CosmosContainerProperties;
import com.azure.cosmos.models.ThroughputProperties;
import com.microsoft.azure.CloudException;
import com.microsoft.azure.credentials.ApplicationTokenCredentials;
import com.microsoft.azure.management.Azure;
import com.microsoft.azure.management.cosmosdb.CosmosDBAccount;
import com.microsoft.azure.management.cosmosdb.DatabaseAccountKind;
import com.microsoft.azure.management.cosmosdb.DatabaseAccountListKeysResult;
import com.microsoft.azure.management.resources.fluentcore.arm.Region;
import com.microsoft.azure.management.resources.fluentcore.utils.SdkContext;
import com.microsoft.azure.management.samples.Utils;
import com.microsoft.rest.LogLevel;

import java.io.File;

/**
 * Azure CosmosDB sample for high availability.
 *  - Create a CosmosDB configured with eventual consistency
 *  - Get the credentials for the CosmosDB
 *  - add container to the CosmosDB
 *  - Delete the CosmosDB.
 */
public final class CreateCosmosDBWithEventualConsistency {
    static final String DATABASE_ID = "TestDB";
    static final String CONTAINER_ID = "TestContainer";

    /**
     * Main function which runs the actual sample.
     * @param azure instance of the azure client
     * @param clientId client id
     * @return true if sample runs successfully
     */
    public static boolean runSample(Azure azure, String clientId) {
        final String docDBName = SdkContext.randomResourceName("docDb", 10);
        final String rgName = SdkContext.randomResourceName("rgNEMV", 24);

        try {
            //============================================================
            // Create a CosmosDB.

            System.out.println("Creating a CosmosDB...");
            CosmosDBAccount cosmosDBAccount = azure.cosmosDBAccounts().define(docDBName)
                    .withRegion(Region.US_WEST)
                    .withNewResourceGroup(rgName)
                    .withKind(DatabaseAccountKind.GLOBAL_DOCUMENT_DB)
                    .withEventualConsistency()
                    .withWriteReplication(Region.US_EAST)
                    .withReadReplication(Region.US_CENTRAL)
                    .create();

            System.out.println("Created CosmosDB");
            Utils.print(cosmosDBAccount);

            //============================================================
            // Get credentials for the CosmosDB.

            System.out.println("Get credentials for the CosmosDB");
            DatabaseAccountListKeysResult databaseAccountListKeysResult = cosmosDBAccount.listKeys();
            String masterKey = databaseAccountListKeysResult.primaryMasterKey();
            String endPoint = cosmosDBAccount.documentEndpoint();

            //============================================================
            // Connect to CosmosDB and add a container

            System.out.println("Connecting and adding container");
            createDBAndAddContainer(masterKey, endPoint);

            //============================================================
            // Delete CosmosDB
            System.out.println("Deleting the CosmosDB");
            // work around CosmosDB service issue returning 404 CloudException on delete operation
            try {
                azure.cosmosDBAccounts().deleteById(cosmosDBAccount.id());
            } catch (CloudException e) {
            }
            System.out.println("Deleted the CosmosDB");

            return true;
        } catch (Exception e) {
            System.err.println(e.getMessage());
        } finally {
            try {
                System.out.println("Deleting resource group: " + rgName);
                azure.resourceGroups().beginDeleteByName(rgName);
                System.out.println("Deleted resource group: " + rgName);
            } catch (NullPointerException npe) {
                System.out.println("Did not create any resources in Azure. No clean up is necessary");
            } catch (Exception g) {
                g.printStackTrace();
            }
        }

        return false;
    }

    private static void createDBAndAddContainer(String masterKey, String endPoint) throws CosmosException {
        try {
            CosmosClient cosmosClient = new CosmosClientBuilder()
                .endpoint(endPoint)
                .key(masterKey)
                .consistencyLevel(ConsistencyLevel.SESSION)
                .buildClient();


            cosmosClient.createDatabase(DATABASE_ID);

            CosmosDatabase cosmosDatabase = cosmosClient.getDatabase(DATABASE_ID);

            System.out.println("Created a new database:");
            System.out.println(cosmosDatabase.getId());

            // Set the provisioned throughput for this container to be 1000 RUs.
            Integer throughput = 1000;
            ThroughputProperties properties = ThroughputProperties.createManualThroughput(throughput);

            // Create a new container.
            CosmosContainerProperties containerProperties = new CosmosContainerProperties(CONTAINER_ID, "/id");
            cosmosDatabase.createContainerIfNotExists(containerProperties, properties);
        } catch (Exception ex) {
            throw ex;
        }
    }

    /**
     * Main entry point.
     * @param args the parameters
     */
    public static void main(String[] args) {
        try {

            //=============================================================
            // Authenticate

            final File credFile = new File(System.getenv("AZURE_AUTH_LOCATION"));

            Azure azure = Azure.configure()
                    .withLogLevel(LogLevel.BASIC)
                    .authenticate(credFile)
                    .withDefaultSubscription();

            // Print selected subscription
            System.out.println("Selected subscription: " + azure.subscriptionId());

            runSample(azure, ApplicationTokenCredentials.fromFile(credFile).clientId());
        } catch (Exception e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
    }

    private CreateCosmosDBWithEventualConsistency() {
    }
}
