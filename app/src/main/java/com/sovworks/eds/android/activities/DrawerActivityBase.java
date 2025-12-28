package com.sovworks.eds.android.activities;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.sovworks.eds.android.R;
import com.sovworks.eds.android.navigdrawer.DrawerController;
import com.sovworks.eds.android.navigdrawer.DrawerControllerBase;

public abstract class DrawerActivityBase extends AppCompatActivity implements DrawerControllerBase.Host
{
    private DrawerController _drawerController;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        super.setContentView(R.layout.drawer_activity_base);

        _drawerController = createDrawerController();
        _drawerController.init(savedStateFromBundle(savedInstanceState));
    }

    protected DrawerController createDrawerController()
    {
        return new DrawerController(this);
    }

    @Override
    public void setContentView(int layoutResID)
    {
        FrameLayout contentFrame = findViewById(R.id.content_frame);
        contentFrame.removeAllViews();
        getLayoutInflater().inflate(layoutResID, contentFrame);
    }

    @Override
    public void setContentView(View view)
    {
        FrameLayout contentFrame = findViewById(R.id.content_frame);
        contentFrame.removeAllViews();
        contentFrame.addView(view);
    }

    @Override
    public void setContentView(View view, ViewGroup.LayoutParams params)
    {
        FrameLayout contentFrame = findViewById(R.id.content_frame);
        contentFrame.removeAllViews();
        contentFrame.addView(view, params);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState)
    {
        super.onPostCreate(savedInstanceState);
        _drawerController.onPostCreate();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig)
    {
        super.onConfigurationChanged(newConfig);
        _drawerController.onConfigurationChanged(newConfig);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        if (_drawerController.onOptionsItemSelected(item))
            return true;
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed()
    {
        if (_drawerController.onBackPressed())
            return;
        super.onBackPressed();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState)
    {
        super.onSaveInstanceState(outState);
        _drawerController.onSaveInstanceState(outState);
    }

    public DrawerController getDrawerController()
    {
        return _drawerController;
    }

    @Override
    public boolean isSelectAction()
    {
        return false;
    }

    private Bundle savedStateFromBundle(Bundle b)
    {
        return b;
    }
}
