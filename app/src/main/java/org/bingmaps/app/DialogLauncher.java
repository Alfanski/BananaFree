package org.bingmaps.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnMultiChoiceClickListener;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.util.DebugUtils;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.bingmaps.bsds.BingSpatialDataService;
import org.bingmaps.bsds.Record;
import org.bingmaps.rest.BingMapsRestService;
import org.bingmaps.rest.RoutePathOutput;
import org.bingmaps.rest.RouteRequest;
import org.bingmaps.rest.models.ItineraryItem;
import org.bingmaps.rest.models.Location;
import org.bingmaps.sdk.BingMapsView;
import org.bingmaps.sdk.Color;
import org.bingmaps.sdk.EntityLayer;
import org.bingmaps.sdk.LocationRect;
import org.bingmaps.sdk.Point;
import org.bingmaps.sdk.Polyline;
import org.bingmaps.sdk.PolylineOptions;
import org.bingmaps.sdk.Pushpin;
import org.bingmaps.sdk.PushpinOptions;
import org.bingmaps.sdk.TileLayer;
import org.bingmaps.sdk.Utilities;

import java.util.HashMap;

public class DialogLauncher {
    private static final String TAG = "DialogLauncher";
    private static String currentBananaFree = "";
    public static void basicReadWrite(String location, boolean bananaFree, String comment) {
        // [START write_message]
        // Write a message to the database
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        DatabaseReference myRef = database.getReference("stations/"+location+"/comment");

        myRef.setValue(comment);
        myRef = database.getReference("stations/"+location+"/bananaFree");
        myRef.setValue(""+bananaFree);
        // [END write_message]

        // [START read_message]
        // Read from the database
        myRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                // This method is called once with the initial value and again
                // whenever data at this location is updated.
                String value = dataSnapshot.getKey();


                Log.d(TAG, "Value is: " + value);
            }

            @Override
            public void onCancelled(DatabaseError error) {
                // Failed to read value
                Log.w(TAG, "Failed to read value.", error.toException());
            }
        });
        // [END read_message]
    }
    public static void LaunchAboutDialog(final Activity activity) {
        final View aboutView = activity.getLayoutInflater().inflate(R.layout.about, (ViewGroup) activity.findViewById(R.id.aboutView));

        AlertDialog.Builder aboutAlert = new AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.about))
                .setIcon(android.R.drawable.ic_dialog_info)
                .setView(aboutView)
                .setPositiveButton(activity.getString(R.string.ok), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        // Canceled. Do nothing
                    }
                });
        aboutAlert.show();
    }

    public static void LaunchLayersDialog(final Activity activity, final BingMapsView bingMapsView, final CharSequence[] dataLayers, boolean[] dataLayerSelections) {
        new AlertDialog.Builder(activity)
                .setIcon(android.R.drawable.ic_menu_slideshow)
                .setTitle(activity.getString(R.string.layers))
                .setMultiChoiceItems(dataLayers, dataLayerSelections, new OnMultiChoiceClickListener() {
                    public void onClick(DialogInterface arg0, int idx, boolean isChecked) {
                        if (dataLayers[idx] == activity.getString(R.string.traffic)) {
                            if (isChecked) {
                                bingMapsView.getLayerManager().addLayer(new TileLayer(activity.getString(R.string.traffic), org.bingmaps.sdk.Constants.TrafficTileLayerURI, 0.5));
                            } else {
                                bingMapsView.getLayerManager().clearLayer(activity.getString(R.string.traffic));
                            }
                        }
                        //Add support for more map data layers here
                    }
                })
                .setPositiveButton(activity.getString(R.string.close), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface arg0, int arg1) {
                    }
                })
                .show();
    }

    public static void LaunchSearchDialog(Activity activity, final BingMapsView bingMapsView, final Handler loadingScreenHandler) {
        final View searchView = activity.getLayoutInflater().inflate(R.layout.search_input, (ViewGroup) activity.findViewById(R.id.searchInputView));

        AlertDialog.Builder searchAlert = new AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.search))
                .setIcon(android.R.drawable.ic_menu_search)
                .setView(searchView)
                .setNegativeButton(activity.getString(R.string.cancel), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        // Canceled. Do nothing
                    }
                })
                .setPositiveButton(activity.getString(R.string.search), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        EditText input = (EditText) searchView.findViewById(R.id.searchInput);
                        String searchText = input.getText().toString().trim();
                        if (!Utilities.isNullOrEmpty(searchText)) {
                            Message viewMsg = new Message();
                            viewMsg.arg1 = 1;
                            loadingScreenHandler.sendMessage(viewMsg);
                            try {
                                BingMapsRestService bmService = new BingMapsRestService(Constants.BingMapsKey);
                                bmService.GeocodeAsyncCompleted = new Handler() {
                                    public void handleMessage(Message msg) {
                                        if (msg.obj != null) {
                                            org.bingmaps.rest.models.Location[] locations = (org.bingmaps.rest.models.Location[]) msg.obj;

                                            org.bingmaps.rest.models.Location l = locations[0];

                                            if (l.Point != null) {
                                                clearLayers(bingMapsView);
                                                EntityLayer searchLayer = (EntityLayer) bingMapsView.getLayerManager().getLayerByName(Constants.DataLayers.Search);



                                                FirebaseDatabase database = FirebaseDatabase.getInstance();
                                                String key = (l.Point.Latitude+"_"+l.Point.Longitude).replace(".", "p");
                                                DatabaseReference bananaFreeRef = database.getReference("stations/"+key+"/bananaFree");
                                                currentBananaFree = "";
                                                listenToValue(bananaFreeRef);
                                                PushpinOptions po = new PushpinOptions();
                                                po.Icon = currentBananaFree == null || currentBananaFree == "" ? Constants.PushpinIcons.Access: Boolean.parseBoolean(currentBananaFree) == true ? Constants.PushpinIcons.Access: Constants.PushpinIcons.NOAccess;
                                                po.Width = 20;
                                                po.Height = 35;
                                                po.Anchor = new Point(4, 35);

                                                Pushpin location = new Pushpin(l.Point, po);
                                                searchLayer.add(location);
                                                bingMapsView.getLayerManager().addLayer(searchLayer);
                                                searchLayer.updateLayer();
                                                bingMapsView.setCenterAndZoom(l.Point, Constants.DefaultSearchZoomLevel);
                                                Message v = new Message();
                                                v.arg1 = 0;
                                                loadingScreenHandler.sendMessage(v);
                                            } else {
                                                Message v = new Message();
                                                v.arg1 = 0;
                                                loadingScreenHandler.sendMessage(v);
                                            }
                                        } else {
                                            Message v = new Message();
                                            v.arg1 = 0;
                                            loadingScreenHandler.sendMessage(v);
                                        }
                                    }
                                };

                                bmService.GeocodeAsync(searchText);
                            } catch (Exception e) {
                                Message v = new Message();
                                v.arg1 = 0;
                                loadingScreenHandler.sendMessage(v);
                            }
                        }
                    }
                });
        searchAlert.show();
    }

    public static void LaunchFailureInputDialog(Activity activity, final BingMapsView bingMapsView, final Handler loadingScreenHandler) {
        final View failureView = activity.getLayoutInflater().inflate(R.layout.failure_input, (ViewGroup) activity.findViewById(R.id.failureInputView));

        AlertDialog.Builder searchAlert = new AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.report_failure))
                .setIcon(android.R.drawable.ic_menu_search)
                .setView(failureView)
                .setNegativeButton(activity.getString(R.string.cancel), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        // Canceled. Do nothing
                    }
                })
                .setPositiveButton(activity.getString(R.string.report), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        EditText input = (EditText) failureView.findViewById(R.id.failureStationName);

                        String failureText = input.getText().toString().trim();
                        if (!Utilities.isNullOrEmpty(failureText)) {
                            Message viewMsg = new Message();
                            viewMsg.arg1 = 1;
                            loadingScreenHandler.sendMessage(viewMsg);
                            try {
                                BingMapsRestService bmService = new BingMapsRestService(Constants.BingMapsKey);
                                bmService.GeocodeAsyncCompleted = new Handler() {
                                    public void handleMessage(Message msg) {
                                        if (msg.obj != null) {
                                            org.bingmaps.rest.models.Location[] locations = (org.bingmaps.rest.models.Location[]) msg.obj;
                                            org.bingmaps.rest.models.Location l = locations[0];

                                            if (l.Point != null) {
                                                clearLayers(bingMapsView);
                                                EntityLayer bananaLayer = (EntityLayer) bingMapsView.getLayerManager().getLayerByName(Constants.DataLayers.Banana);


                                                EditText comment = (EditText) failureView.findViewById(R.id.failureComment);
                                                basicReadWrite((l.Point.Latitude+"_"+l.Point.Longitude).replace(".","p"), false,comment.getText().toString().trim());

                                                PushpinOptions po = new PushpinOptions();
                                                po.Icon = Constants.PushpinIcons.NOAccess;
                                                po.Width = 20;
                                                po.Height = 35;
                                                po.Anchor = new Point(4, 35);

                                                Pushpin location = new Pushpin(l.Point, po);
                                                bananaLayer.add(location);
                                                bingMapsView.getLayerManager().addLayer(bananaLayer);
                                                bananaLayer.updateLayer();
                                                bingMapsView.setCenterAndZoom(l.Point, Constants.DefaultSearchZoomLevel);
                                                Message v = new Message();
                                                v.arg1 = 0;
                                                loadingScreenHandler.sendMessage(v);
                                            } else {
                                                Message v = new Message();
                                                v.arg1 = 0;
                                                loadingScreenHandler.sendMessage(v);
                                            }
                                        } else {
                                            Message v = new Message();
                                            v.arg1 = 0;
                                            loadingScreenHandler.sendMessage(v);
                                        }
                                    }
                                };

                                bmService.GeocodeAsync(failureText);
                            } catch (Exception e) {
                                Message v = new Message();
                                v.arg1 = 0;
                                loadingScreenHandler.sendMessage(v);
                            }
                        }
                    }
                });
        searchAlert.show();
    }

    public static void listenToValue(DatabaseReference dbRef){
        dbRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                currentBananaFree = dataSnapshot.getValue(String.class);
                //do what you want with the email

            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

    public static void clearLayers(BingMapsView bingMapsView){
        EntityLayer routeLayer = (EntityLayer) bingMapsView.getLayerManager().getLayerByName(Constants.DataLayers.Route);
        EntityLayer bananaLayer = (EntityLayer) bingMapsView.getLayerManager().getLayerByName(Constants.DataLayers.Banana);
        EntityLayer searchLayer = (EntityLayer) bingMapsView.getLayerManager().getLayerByName(Constants.DataLayers.Search);

        if (routeLayer == null) {
            routeLayer = new EntityLayer(Constants.DataLayers.Route);
            bingMapsView.getLayerManager().addLayer(routeLayer);
        }
        if (bananaLayer == null) {
            bananaLayer = new EntityLayer(Constants.DataLayers.Banana);
            bingMapsView.getLayerManager().addLayer(bananaLayer);
        }
        if (searchLayer == null) {
            searchLayer = new EntityLayer(Constants.DataLayers.Search);
            bingMapsView.getLayerManager().addLayer(searchLayer);
        }

        bananaLayer.clear();
        routeLayer.clear();
        searchLayer.clear();
    }

    public static boolean renderRoute(org.bingmaps.rest.models.Route route, int routeIndex, BingMapsView bingMapsView){
        boolean result = true;

        clearLayers(bingMapsView);
        EntityLayer routeLayer = (EntityLayer) bingMapsView.getLayerManager().getLayerByName(Constants.DataLayers.Route);
        FirebaseDatabase database = FirebaseDatabase.getInstance();

        PushpinOptions pOption1 = new PushpinOptions();
        pOption1.Icon = Constants.PushpinIcons.BigStart;
        pOption1.Width = 43;
        pOption1.Height = 55;
        pOption1.Anchor = new Point(20, 36);
        // pOption1.ZIndex = 1000;

        Pushpin start = new Pushpin(route.RouteLegs.get(0).ActualStart, pOption1);
        routeLayer.add(start);

        for(ItineraryItem item: route.RouteLegs.get(0).ItineraryItems){
            PushpinOptions viaOption = pOption1.clone();
            String key = (item.ManeuverPoint.Latitude+"_"+item.ManeuverPoint.Longitude).replace(".", "p");
            DatabaseReference bananaFreeRef = database.getReference("stations/"+key+"/bananaFree");
            currentBananaFree = "";
            listenToValue(bananaFreeRef);
            pOption1.Icon = currentBananaFree == null || currentBananaFree == "" ? Constants.PushpinIcons.Access: Boolean.parseBoolean(currentBananaFree) == true ? Constants.PushpinIcons.Access: Constants.PushpinIcons.NOAccess;
            result = !(currentBananaFree.equals("false"));
            Pushpin viaPushpin = new Pushpin(item.ManeuverPoint, viaOption);
            routeLayer.add(viaPushpin);
        }
        PushpinOptions pOption2 = pOption1.clone();
        pOption2.Icon = Constants.PushpinIcons.BigEnd;
        pOption2.Text = route.RouteLegs.get(0).EndLocation.Name;

        Pushpin end = new Pushpin(route.RouteLegs.get(0).ActualEnd, pOption2);
        routeLayer.add(end);

                                            /*PolylineOptions polyOptions = new PolylineOptions();
                                            polyOptions.StrokeColor = new Color((byte) 0, (byte) 0, (byte) 0, (byte) 0 );
                                            polyOptions.StrokeThickness = 25;*/

        Polyline routeLine = new Polyline(route.RoutePath);
        // routeLine.Options = polyOptions;
        routeLayer.add(routeLine);

        bingMapsView.getLayerManager().addLayer(routeLayer);
        routeLine = null;

        routeLayer.updateLayer();

        if (route.BoundingBox != null) {
            bingMapsView.setMapView(route.BoundingBox);
        }

        route = null;
        return result;
    }

    public static void LaunchDirectionsDialog(final Activity activity, final BingMapsView bingMapsView, final Handler loadingScreenHandler) {
        final View directionsView = activity.getLayoutInflater().inflate(R.layout.directions_input, (ViewGroup) activity.findViewById(R.id.directionsInputView));

        AlertDialog.Builder directionsAlert = new AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.directions))
                .setView(directionsView)
                .setIcon(android.R.drawable.ic_menu_directions)
                .setNegativeButton(activity.getString(R.string.cancel), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        // Canceled. Do nothing
                    }
                })
                .setPositiveButton(activity.getString(R.string.getDirections), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        final EditText fromInput = (EditText) directionsView.findViewById(R.id.directionsFromInput);
                        final EditText toInput = (EditText) directionsView.findViewById(R.id.directionsToInput);

                        String fromText = fromInput.getText().toString().trim();
                        String toText = toInput.getText().toString().trim();

                        if (!Utilities.isNullOrEmpty(fromText) && !Utilities.isNullOrEmpty(toText)) {
                            Message viewMsg = new Message();
                            viewMsg.arg1 = 1;
                            loadingScreenHandler.sendMessage(viewMsg);
                            try {
                                BingMapsRestService bmService = new BingMapsRestService(Constants.BingMapsKey);
                                bmService.RouteAsyncCompleted = new Handler() {
                                    public void handleMessage(Message msg) {
                                        if (msg.obj != null) {
                                            org.bingmaps.rest.models.Route route = (org.bingmaps.rest.models.Route) msg.obj;
                                            boolean bananaFree = renderRoute(route, 0, bingMapsView);
                                            msg.obj = null;
                                        }

                                        Message v = new Message();
                                        v.arg1 = 0;
                                        loadingScreenHandler.sendMessage(v);
                                    }
                                };

                                RouteRequest rr = new RouteRequest();
                                rr.addWaypoint(fromText);
                                rr.addWaypoint(toText);
                                //rr.setRoutePathOutput(RoutePathOutput.Points);

                                bmService.RouteAsync(rr);
                            } catch (Exception e) {
                                Message v = new Message();
                                v.arg1 = 0;
                                loadingScreenHandler.sendMessage(v);
                            }
                        }
                    }
                });

        directionsAlert.show();
    }

    public static void LaunchEntityDetailsDialog(final Activity activity, final HashMap<String, Object> metadata) {
        if (metadata.size() > 0) {
            if (metadata.containsKey("record") && metadata.get("record").getClass() == Record.class) {
                Record record = (Record) metadata.get("record");

                String title = Utilities.isNullOrEmpty(record.DisplayName) ?
                        activity.getString(R.string.details) : record.DisplayName;

                final ScrollView detailsView = (ScrollView) activity.getLayoutInflater().inflate(R.layout.details_view, (ViewGroup) activity.findViewById(R.id.detailsView));

                if (record.Address != null) {
                    TextView addressView = (TextView) detailsView.findViewById(R.id.detailsAddress);
                    addressView.setText(activity.getString(R.string.address) + record.Address.toString());
                }

                if (!Utilities.isNullOrEmpty(record.Phone)) {
                    final String phone = "tel:" + record.Phone;

                    ImageButton phoneBtn = (ImageButton) detailsView.findViewById(R.id.detailsPhoneBtn);
                    phoneBtn.setOnClickListener(new OnClickListener() {
                        public void onClick(View v) {
                            //Call phone number
                            Intent i = new Intent(Intent.ACTION_DIAL, Uri.parse(phone));
                            activity.startActivity(i);
                        }
                    });
                    phoneBtn.setVisibility(View.VISIBLE);
                }

                //OPTION Add custom content to view
                LinearLayout ccView = (LinearLayout) detailsView.findViewById(R.id.detailsCustomContent);

                if (record.Metadata.containsKey("Manager")) {
                    String manager = (String) record.Metadata.get("Manager");
                    if (!Utilities.isNullOrEmpty(manager)) {
                        TextView managerView = new TextView(activity);
                        managerView.setText("Manager: " + manager);
                        ccView.addView(managerView);
                    }
                }

                if (record.Metadata.containsKey("StoreType")) {
                    String storeType = (String) record.Metadata.get("StoreType");
                    if (!Utilities.isNullOrEmpty(storeType)) {
                        TextView storeTypeView = new TextView(activity);
                        storeTypeView.setText("Store Type: " + storeType);
                        ccView.addView(storeTypeView);
                    }
                }

                AlertDialog.Builder detailsAlert = new AlertDialog.Builder(activity)
                        .setTitle(title)
                        .setView(detailsView)
                        .setNegativeButton(activity.getString(R.string.close), new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                // Canceled. Do nothing
                            }
                        });
                detailsAlert.show();
            } else {
                //OPTION add support for HashMap so that data other than records
                //from the Bing Spatial Data Services can be rendered.
            }
        }
    }

    public static void LaunchOverrideCultureDialog(final Activity activity, final BingMapsView bingMapsView) {
        final View cultureView = activity.getLayoutInflater().inflate(R.layout.culture_input, (ViewGroup) activity.findViewById(R.id.cultureInputView));

        AlertDialog.Builder cultureAlert = new AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.culture_mkt_param))
                .setIcon(android.R.drawable.ic_menu_search)
                .setView(cultureView)
                .setNegativeButton(activity.getString(R.string.cancel), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        // Canceled. Do nothing
                    }
                })
                .setPositiveButton(activity.getString(R.string.change), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        EditText input = (EditText) cultureView.findViewById(R.id.cultureInput);
                        bingMapsView.overrideCulture(input.getText().toString().trim());
                    }
                });
        cultureAlert.show();
    }

    public static void DemoDataApi(final BingMapsView bingMapsView, final Handler loadingScreenHandler, org.bingmaps.rest.models.Location l) {
        //Search for nearby locations
        BingSpatialDataService bsds = new BingSpatialDataService(
                Constants.BingSpatialAccessId,
                Constants.BingSpatialDataSourceName,
                Constants.BingSpatialEntityTypeName,
                Constants.BingSpatialQueryKey);

        //Perform a nearby search for POI data
        bsds.FindByAreaCompleted = new Handler() {
            public void handleMessage(Message msg) {
                if (msg.obj != null) {
                    Record[] records = (Record[]) msg.obj;
                    EntityLayer el = (EntityLayer) bingMapsView.getLayerManager().getLayerByName(Constants.DataLayers.Search);
                    double maxLat = -90, minLat = 90, maxLon = -180, minLon = 180;

                    for (Record r : records) {
                        Pushpin p = new Pushpin(r.Location);
                        p.Title = r.DisplayName;

                        if (r.Location.Latitude > maxLat) {
                            maxLat = r.Location.Latitude;
                        }
                        if (r.Location.Longitude > maxLon) {
                            maxLon = r.Location.Longitude;
                        }
                        if (r.Location.Latitude < minLat) {
                            minLat = r.Location.Latitude;
                        }
                        if (r.Location.Longitude < minLon) {
                            minLon = r.Location.Longitude;
                        }

                        HashMap<String, Object> metadata = new HashMap<String, Object>();
                        metadata.put("record", r);
                        el.add(p, metadata);
                    }

                    bingMapsView.setMapView(new LocationRect(maxLat, maxLon, minLat, minLon));

                    el.updateLayer();
                }

                Message v = new Message();
                v.arg1 = 0;
                loadingScreenHandler.sendMessage(v);
            }
        };

        bsds.FindByArea(l.Point, Constants.SearchRadiusKM, null);
    }

    public static void LaunchAccessibleDialog(Activity activity, final BingMapsView bingMapsView, final Handler loadingScreenHandler) {
        final View accessibleView = activity.getLayoutInflater().inflate(R.layout.accessible_input, (ViewGroup) activity.findViewById(R.id.accessibleView));

        AlertDialog.Builder accessibleAlert = new AlertDialog.Builder(activity)
                .setTitle("Confirm Accessibility")
                .setIcon(android.R.drawable.ic_menu_crop)
                .setView(accessibleView)
                .setNegativeButton(activity.getString(R.string.cancel), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        // Canceled. Do nothing
                    }
                })
                .setPositiveButton("Confirm", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        EditText input = (EditText) accessibleView.findViewById(R.id.accessibleInput);
                        String accessibilityText = input.getText().toString().trim();
                        if (!Utilities.isNullOrEmpty(accessibilityText)) {
                            Message viewMsg = new Message();
                            viewMsg.arg1 = 1;
                            loadingScreenHandler.sendMessage(viewMsg);
                            try {
                                BingMapsRestService bmService = new BingMapsRestService(Constants.BingMapsKey);
                                bmService.GeocodeAsyncCompleted = new Handler() {
                                    public void handleMessage(Message msg) {
                                        if (msg.obj != null) {
                                            org.bingmaps.rest.models.Location[] locations = (org.bingmaps.rest.models.Location[]) msg.obj;
                                            org.bingmaps.rest.models.Location l = locations[0];

                                            if (l.Point != null) {
                                                clearLayers(bingMapsView);
                                                EntityLayer searchLayer = (EntityLayer) bingMapsView.getLayerManager().getLayerByName(Constants.DataLayers.Search);

                                                basicReadWrite((l.Point.Latitude+"_"+l.Point.Longitude).replace(".","p"),true, "");

                                                PushpinOptions po = new PushpinOptions();
                                                po.Icon = Constants.PushpinIcons.Access;
                                                po.Width = 20;
                                                po.Height = 35;
                                                po.Anchor = new Point(4, 35);

                                                Pushpin location = new Pushpin(l.Point, po);
                                                searchLayer.add(location);
                                                bingMapsView.getLayerManager().addLayer(searchLayer);
                                                searchLayer.updateLayer();
                                                bingMapsView.setCenterAndZoom(l.Point, Constants.DefaultSearchZoomLevel);
                                                Message v = new Message();
                                                v.arg1 = 0;
                                                loadingScreenHandler.sendMessage(v);
                                            } else {
                                                Message v = new Message();
                                                v.arg1 = 0;
                                                loadingScreenHandler.sendMessage(v);
                                            }
                                        } else {
                                            Message v = new Message();
                                            v.arg1 = 0;
                                            loadingScreenHandler.sendMessage(v);
                                        }
                                    }
                                };

                                bmService.GeocodeAsync(accessibilityText);
                            } catch (Exception e) {
                                Message v = new Message();
                                v.arg1 = 0;
                                loadingScreenHandler.sendMessage(v);
                            }
                        }
                    }
                });
        accessibleAlert.show();
    }
}
