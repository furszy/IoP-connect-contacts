package com.example.furszy.contactsapp;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import com.example.furszy.contactsapp.ui.home.HomeActivity;
import com.example.furszy.contactsapp.ui.my_qr.MyQrActivity;
import com.example.furszy.contactsapp.ui.settings.SettingsActivity;
import org.fermat.redtooth.profile_server.model.Profile;
import java.util.Arrays;
import de.hdodenhof.circleimageview.CircleImageView;

/**
 * Created by Neoperol on 6/20/17.
 */

public class BaseDrawerActivity extends BaseActivity implements NavigationView.OnNavigationItemSelectedListener, View.OnClickListener {

    private NavigationView navigationView;
    private FrameLayout frameLayout;
    private Toolbar toolbar;
    private DrawerLayout drawer;

    private View navHeader;
    private CircleImageView imgProfile;
    private TextView txtName;

    private byte[] cachedProfImage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        beforeCreate();
        setContentView(R.layout.activity_base_drawer);
        toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        frameLayout = (FrameLayout) findViewById(R.id.content_frame);
        drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.setDrawerListener(toggle);
        toggle.syncState();

        navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        navHeader = navigationView.getHeaderView(0);
        navHeader.setOnClickListener(this);

        imgProfile = (CircleImageView) navHeader.findViewById(R.id.profile_image);
        txtName = (TextView) navHeader.findViewById(R.id.txt_name);

        onCreateView(savedInstanceState,frameLayout);

    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshProfile();
    }

    private void refreshProfile() {
        if (anRedtooth!=null) {
            Profile profile = anRedtooth.getProfile();
            txtName.setText(profile.getName());
            if (profile.getImg()!=null) {
                if (cachedProfImage == null || !Arrays.equals(profile.getImg(), cachedProfImage)) {
                    cachedProfImage = profile.getImg();
                    Bitmap bitmap = BitmapFactory.decodeByteArray(cachedProfImage, 0, cachedProfImage.length);
                    imgProfile.setImageBitmap(bitmap);
                }
            }
        }
    }

    /**
     * Empty method to check some status before set the main layout of the activity
     */
    protected void beforeCreate(){

    }

    /**
     * Empty method to override.
     *
     * @param savedInstanceState
     */
    protected void onCreateView(Bundle savedInstanceState, ViewGroup container){

    }

    @Override
    public void onBackPressed() {
        drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        int id = item.getItemId();


        return super.onOptionsItemSelected(item);
    }


    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        //to prevent current item select over and over
        if (item.isChecked()){
            drawer.closeDrawer(GravityCompat.START);
            return false;
        }

        if (id == R.id.nav_contact) {
            startActivity(new Intent(getApplicationContext(), HomeActivity.class));
        } else if (id == R.id.nav_settings) {
            startActivity(new Intent(getApplicationContext(), SettingsActivity.class));
        } else if (id == R.id.nav_qr_code) {
            startActivity(new Intent(getApplicationContext(), MyQrActivity.class));
        }

        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    protected void setNavigationMenuItemChecked(int pos){
        navigationView.getMenu().getItem(pos).setChecked(true);
    }


    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id==R.id.container_profile){
            startActivity(new Intent(v.getContext(),ProfileActivity.class));
        }
    }
}