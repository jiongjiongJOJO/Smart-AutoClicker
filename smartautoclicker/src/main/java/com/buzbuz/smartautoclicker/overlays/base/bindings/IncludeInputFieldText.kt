/*
 * Copyright (C) 2022 Kevin Buzeau
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.buzbuz.smartautoclicker.overlays.base.bindings

import android.text.Editable

import androidx.annotation.StringRes

import com.buzbuz.smartautoclicker.R
import com.buzbuz.smartautoclicker.baseui.OnAfterTextChangedListener
import com.buzbuz.smartautoclicker.databinding.IncludeInputFieldTextBinding

fun IncludeInputFieldTextBinding.setLabel(@StringRes labelResId: Int) {
    root.setHint(labelResId)
}

fun IncludeInputFieldTextBinding.setText(text: String?) {
    textField.setText(text)
}

fun IncludeInputFieldTextBinding.setError(isError: Boolean) {
    root.error = if (isError) root.context.getString(R.string.input_field_error_required) else null
}

fun IncludeInputFieldTextBinding.setOnTextChangedListener(listener: (Editable) -> Unit) {
    textField.addTextChangedListener(OnAfterTextChangedListener(listener))
}
