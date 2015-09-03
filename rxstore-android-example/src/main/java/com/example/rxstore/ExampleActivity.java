/*
 * Copyright (C) GRIDSTONE 2015
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

package com.example.rxstore;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import au.com.gridstone.rxstore.RxStore;
import au.com.gridstone.rxstore.converters.GsonConverter;
import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import com.example.rxstore.helpers.ArmLengthAdapter;
import com.example.rxstore.helpers.DinoView;
import com.example.rxstore.helpers.RandomDinoGenerator;
import java.util.List;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.schedulers.Schedulers;

public class ExampleActivity extends Activity {
  private static final String PERSISTENCE_DIR = "grexPersistence";
  private static final String KEY_SINGLE_DINO = "dino";
  private static final String KEY_MULTI_DINOS = "dinoList";

  @Bind(R.id.single_dino_name) TextView nameView;
  @Bind(R.id.single_dino_arm_length) TextView armLengthView;
  @Bind(R.id.single_dino_name_input) EditText nameInputView;
  @Bind(R.id.single_dino_arm_length_input) Spinner armLengthSpinner;
  @Bind(R.id.dino_list_container) ViewGroup dinoListContainer;

  private RxStore rxStore;

  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.activity_example);
    ButterKnife.bind(this);
    rxStore = RxStore.with(this).using(new GsonConverter());

    loadDino();
    loadDinoList();
    setupInput();
  }

  private void loadDino() {
    rxStore.get(KEY_SINGLE_DINO, Dino.class)
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(new Action1<Dino>() {
          @Override public void call(Dino dino) {
            if (dino != null) {
              nameView.setText(dino.name);
              armLengthView.setText(dino.armLength + "cm");
            }
          }
        });
  }

  private void loadDinoList() {
    rxStore.getList(KEY_MULTI_DINOS, Dino.class)
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(new Action1<List<Dino>>() {
          @Override public void call(List<Dino> dinos) {
            displayDinoList(dinos);
          }
        });
  }

  private void setupInput() {
    final ArmLengthAdapter adapter = new ArmLengthAdapter(this);
    armLengthSpinner.setAdapter(adapter);
    armLengthSpinner.setSelection(ArmLengthAdapter.getPositionForValue(4));
  }

  private void displayDinoList(List<Dino> dinos) {
    dinoListContainer.removeAllViews();

    if (dinos == null) {
      return;
    }

    LayoutInflater inflater = getLayoutInflater();

    for (Dino dino : dinos) {
      DinoView dinoView = (DinoView) inflater.inflate(R.layout.view_dino, dinoListContainer, false);
      dinoView.nameView.setText(dino.name);
      dinoView.armLengthView.setText(dino.armLength + "cm");
      dinoListContainer.addView(dinoView);
    }
  }

  @OnClick(R.id.single_object_persist_button) public void onPersistClick() {
    String name = nameInputView.getText().toString();
    int armLength = (Integer) armLengthSpinner.getSelectedItem();
    final Dino dino = new Dino(name, armLength);

    rxStore.put(KEY_SINGLE_DINO, dino)
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(new Action1<Dino>() {
          @Override public void call(Dino dino) {
            nameView.setText(dino.name);
            armLengthView.setText(dino.armLength + "cm");
          }
        });
  }

  @OnClick(R.id.dino_list_add_button) public void onAddDinoClick() {
    rxStore.addToList(KEY_MULTI_DINOS, RandomDinoGenerator.spawn(), Dino.class)
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(new Action1<List<Dino>>() {
          @Override public void call(List<Dino> dinos) {
            displayDinoList(dinos);
          }
        });
  }

  @OnClick(R.id.dino_list_remove_button) public void onRemoveDinoClick() {
    int dinoViewCount = dinoListContainer.getChildCount();

    if (dinoViewCount < 1) {
      return;
    }

    rxStore.removeFromList(KEY_MULTI_DINOS, dinoViewCount - 1, Dino.class)
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(new Action1<List<Dino>>() {
          @Override public void call(List<Dino> dinos) {
            displayDinoList(dinos);
          }
        });
  }
}