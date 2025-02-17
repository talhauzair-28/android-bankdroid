/*
 * Copyright (C) 2010 Nullbyte <http://nullbyte.eu>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.liato.bankdroid;

import com.google.common.collect.Iterators;

import com.crashlytics.android.Crashlytics;
import com.liato.bankdroid.api.configuration.Entry;
import com.liato.bankdroid.api.configuration.Field;
import com.liato.bankdroid.appwidget.AutoRefreshService;
import com.liato.bankdroid.banking.Account;
import com.liato.bankdroid.banking.Bank;
import com.liato.bankdroid.banking.BankChoice;
import com.liato.bankdroid.banking.BankFactory;
import com.liato.bankdroid.banking.exceptions.BankChoiceException;
import com.liato.bankdroid.banking.exceptions.BankException;
import com.liato.bankdroid.banking.exceptions.LoginException;
import com.liato.bankdroid.configuration.DefaultConnectionConfiguration;
import com.liato.bankdroid.db.DBAdapter;
import com.liato.bankdroid.utils.FieldTypeMapper;
import com.liato.bankdroid.utils.NetworkUtils;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import butterknife.ButterKnife;
import butterknife.InjectView;
import butterknife.OnClick;

public class BankEditActivity extends LockableActivity implements OnItemSelectedListener {

    @InjectView(R.id.spnBankeditBanklist)
    Spinner mBankSpinner;

    @InjectView(R.id.layoutBankConfiguration)
    LinearLayout mFormContainer;

    @InjectView(R.id.txtErrorDesc)
    TextView mErrorDescription;

    private final static String TAG = "BankEditActivity";

    private Bank SELECTED_BANK;

    private long BANKID = -1;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.bank);
        ButterKnife.inject(this);
        this.getWindow()
                .setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        List<Bank> items = BankFactory.listBanks(this);
        Collections.sort(items);
      final  BankSpinnerAdapter<Bank> adapter = new BankSpinnerAdapter<>(this, items);
        mBankSpinner.setAdapter(adapter);


        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            BANKID = extras.getLong("id", -1);
            if (BANKID != -1) {
                Bank bank = BankFactory.bankFromDb(BANKID, this, false);
                if (bank != null) {
                    mErrorDescription.setVisibility(
                            bank.isDisabled() ? View.VISIBLE : View.INVISIBLE);
                    mBankSpinner.setEnabled(false);
                    mBankSpinner.setSelection(adapter.getPosition(bank));
                    SELECTED_BANK = bank;
                    createForm(SELECTED_BANK.getConnectionConfiguration(),
                            DefaultConnectionConfiguration.fields()
                    );
                    populateForm(bank);
                }
            }
        }


        EditText tsearch = (EditText)findViewById(R.id.etSearch);

        tsearch.addTextChangedListener(new TextWatcher(){

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count,
                                          int after) {
                // TODO Auto-generated method stub

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before,
                                      int count) {
                // TODO Auto-generated method stub

                adapter.getFilter().filter(s);
            }

            @Override
            public void afterTextChanged(Editable s) {
                // TODO Auto-generated method stub

            }});





    mBankSpinner.setOnItemSelectedListener(this);
    }

    @OnClick(R.id.btnSettingsOk)
    public void onSubmit(View v) {
        if(!validate()) {
            return;
        }
        SELECTED_BANK.setProperties(getFormParameters(SELECTED_BANK.getConnectionConfiguration()));
        SELECTED_BANK.setCustomName(getFormParameter(DefaultConnectionConfiguration.NAME));
        SELECTED_BANK.setDbid(BANKID);
        new DataRetrieverTask(this, SELECTED_BANK).execute();
    }

    @OnClick(R.id.btnSettingsCancel)
    public void onCancel(View v) {
        this.finish();
    }

    @Override
    public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int pos, long id) {
        Bank selectedBank = (Bank) parentView.getItemAtPosition(pos);
        if(SELECTED_BANK == null || !SELECTED_BANK.equals(selectedBank)) {
            SELECTED_BANK = selectedBank;
                    mFormContainer.removeAllViewsInLayout();
            createForm(SELECTED_BANK.getConnectionConfiguration(),
                    DefaultConnectionConfiguration.fields()
            );
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> arg) {
    }


    private void createForm(List<Field>... configurations) {
        for(List<Field> fields : configurations) {
            for (Field field : fields) {
                createLabel(field);
                if(field.getValues().isEmpty()) {
                    createField(field);
                } else {
                    createSpinner(field);
                }
            }
        }
    }

    private void createLabel(Field field) {
        TextView fieldText = new TextView(this);
        String label = field.getLabel() + (field.isRequired() ? "" : " " + getString(R.string.optional_field));
        fieldText.setText(label);
        fieldText.setVisibility(field.isHidden() ? View.GONE : View.VISIBLE);
        mFormContainer.addView(fieldText);
    }

    private void createField(Field field) {
        EditText inputField = new EditText(this);
        inputField.setHint(field.getPlaceholder());
        if (field.isSecret()) {
            inputField.setTransformationMethod(
                    PasswordTransformationMethod.getInstance());
        } else {
            inputField
                    .setInputType(FieldTypeMapper.fromFieldType(field.getFieldType()));
        }
        inputField.setVisibility(field.isHidden() ? View.GONE : View.VISIBLE);
        inputField.setTag(field.getReference());

        mFormContainer.addView(inputField);
    }

    private void createSpinner(Field field) {
        Spinner spinner = new Spinner(this);
        spinner.setAdapter(new ArrayAdapter<Entry>(this, android.R.layout.simple_spinner_item,
                field.getValues()));
        spinner.setTag(field.getReference());
        mFormContainer.addView(spinner);
    }

    private void populateForm(Bank bank) {
        EditText customName = (EditText) mFormContainer.findViewWithTag(
                DefaultConnectionConfiguration.NAME);
        customName.setText(bank.getCustomName());

        for(Map.Entry<String, String> property : bank.getProperties().entrySet()) {
            EditText propertyInput = (EditText) mFormContainer.findViewWithTag(property.getKey());
            propertyInput.setText(property.getValue());
        }
    }

    private Map<String, String> getFormParameters(List<Field> fields) {
        Map<String, String> properties = new HashMap<>();
        for(Field field : fields) {
            properties.put(field.getReference(), getFormParameter(field.getReference()));
        }
        return properties;
    }

    private String getFormParameter(String property) {
        View propertyView = mFormContainer.findViewWithTag(property);
        if(propertyView instanceof EditText) {
            EditText propertyInput = (EditText) propertyView;
            return propertyInput.getText().toString().trim();
        } else if(propertyView instanceof Spinner) {
            Spinner spinnerProperty = (Spinner) propertyView;
            Entry entry = (Entry) spinnerProperty.getSelectedItem();
            return entry.getKey();
        } else {
            return null;
        }
    }

    private boolean validate() {
        boolean valid = true;
        Iterator<Field> fields = Iterators.concat(SELECTED_BANK.getConnectionConfiguration().iterator(),
                DefaultConnectionConfiguration.fields().iterator());
        while(fields.hasNext()) {
            Field field = fields.next();
            try {
                field.validate(getFormParameter(field.getReference()));
            } catch (IllegalArgumentException e) {
                valid = false;
                ((EditText) mFormContainer.findViewWithTag(field.getReference())).setError(e.getMessage());
            }
        }
        return valid;
    }

    private class BankSpinnerAdapter<T> extends ArrayAdapter<T> {

        private LayoutInflater inflater;
        List<Bank> list;
        List<Bank> list2;
        ValueFilter valueFilter;

        public BankSpinnerAdapter(Context context, List<T> items) {
            super(context, R.layout.bank_spinner_item, R.id.txtBank, items);
            inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            list= BankFactory.listBanks(context);
            Collections.sort(list);
            list2 = list;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = inflater.inflate(R.layout.bank_spinner_item, parent, false);
            }
      /*      ((TextView) convertView.findViewById(R.id.txtBank))
                    .setText(((Bank) getItem(position)).getName());
            ((ImageView) convertView.findViewById(R.id.imgBank))
                    .setImageResource(((Bank) getItem(position)).getImageResource());

*/


            ((TextView) convertView.findViewById(R.id.txtBank))
                    .setText((list.get(position)).getName());
            ((ImageView) convertView.findViewById(R.id.imgBank))
                    .setImageResource((list.get(position)).getImageResource());


            return convertView;
        }

        @Override
        public View getDropDownView(int position, View convertView,
                ViewGroup parent) {
            if (convertView == null) {
                convertView = inflater.inflate(R.layout.bank_spinner_dropdown_item, parent, false);
            }
         /*   ((TextView) convertView.findViewById(R.id.txtBank))
                    .setText(((Bank) getItem(position)).getName());
            ((ImageView) convertView.findViewById(R.id.imgBank))
                    .setImageResource(((Bank) getItem(position)).getImageResource());
            */

            ((TextView) convertView.findViewById(R.id.txtBank))
                    .setText((list.get(position)).getName());

            ((ImageView) convertView.findViewById(R.id.imgBank))
                    .setImageResource((list.get(position)).getImageResource());


            return convertView;
        }

        @Override
        public Filter getFilter() {
            // TODO Auto-generated method stub

            if(valueFilter == null)
            {
                valueFilter = new ValueFilter();
            }
            return valueFilter;
        }
        private class ValueFilter extends Filter {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {

                FilterResults results = new FilterResults();

                if (constraint != null && constraint.length() > 0) {
                    ArrayList<Bank> filterList = new ArrayList<Bank>();
                    list = list2;
                    for (int i = 0; i < list.size(); i++)
                    {
                        if ( (list.get(i).getName().toUpperCase() )
                                .contains(constraint.toString().toUpperCase()) ) {


                            Bank newObj = list.get(i);
                            filterList.add(newObj);
                        }
                    }
                    results.count = filterList.size();
                    results.values = filterList;
                } else {
                    results.count = list2.size();
                    results.values = list2;
                }
                return results;  }



            @SuppressWarnings("unchecked")
            @Override
            protected void publishResults(CharSequence constraint,
                                          FilterResults results) {
                list= (ArrayList<Bank>) results.values;
                notifyDataSetChanged();
            }

        }
        @Override
        public int getCount() {
            // TODO Auto-generated method stub
            if(list != null)
            {
                return list.size();
            }else
            {
                return 0;
            }
        }




    }

    private class DataRetrieverTask extends AsyncTask<String, Void, Void> {

        private final ProgressDialog dialog = new ProgressDialog(BankEditActivity.this);

        private Exception exc = null;

        private Bank bank;

        private BankEditActivity context;

        private Resources res;

        public DataRetrieverTask(BankEditActivity context, Bank bank) {
            this.context = context;
            this.res = context.getResources();
            this.bank = bank;
        }

        protected void onPreExecute() {
            this.dialog.setMessage(res.getText(R.string.logging_in));
            this.dialog.show();
        }

        protected Void doInBackground(final String... args) {
            try {
                bank.update();
                bank.updateAllTransactions();
                bank.closeConnection();
                DBAdapter.save(bank, context);

                // Transactions updated.
                final SharedPreferences prefs = PreferenceManager
                        .getDefaultSharedPreferences(getBaseContext());
                if (prefs.getBoolean("content_provider_enabled", false)) {
                    final ArrayList<Account> accounts = bank.getAccounts();
                    for (final Account account : accounts) {
                        AutoRefreshService.broadcastTransactionUpdate(
                                getBaseContext(), bank.getDbId(),
                                account.getId());
                    }
                }
            } catch (BankException e) {
                this.exc = e;
                Crashlytics.logException(e);
            } catch (LoginException e) {
                this.exc = e;
            } catch (BankChoiceException e) {
                this.exc = e;
            } catch (IOException e) {
                this.exc = e;
                if (NetworkUtils.isInternetAvailable()) {
                    Crashlytics.logException(e);
                }
            }
            return null;
        }

        protected void onPostExecute(final Void unused) {
            AutoRefreshService.sendWidgetRefresh(context);
            ActivityHelper.dismissDialog(this.dialog);
            if (this.exc != null) {
                AlertDialog.Builder builder = new AlertDialog.Builder(context);
                if (this.exc instanceof BankChoiceException) {
                    final BankChoiceException e = (BankChoiceException) exc;
                    final String[] items = new String[e.getBanks().size()];
                    int i = 0;
                    for (BankChoice b : e.getBanks()) {
                        items[i] = b.getName();
                        i++;
                    }
                    builder.setTitle(R.string.select_a_bank);
                    builder.setItems(items, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int item) {
                            SELECTED_BANK.setExtras(e.getBanks().get(item).getId());
                            new DataRetrieverTask(context, SELECTED_BANK).execute();
                        }
                    });
                } else {
                    exc.printStackTrace();
                    builder.setMessage(this.exc.getMessage())
                            .setTitle(res.getText(R.string.could_not_create_account))
                            .setIcon(android.R.drawable.ic_dialog_alert)
                            .setNeutralButton("Ok", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    dialog.cancel();
                                }
                            });
                }
                AlertDialog alert = builder.create();
                if (!context.isFinishing()) {
                    alert.show();
                }
            } else {
                context.finish();
            }
        }
    }
}
