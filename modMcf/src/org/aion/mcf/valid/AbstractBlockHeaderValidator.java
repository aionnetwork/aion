/*
 * Copyright (c) 2017-2018 Aion foundation.
 *
 *     This file is part of the aion network project.
 *
 *     The aion network project is free software: you can redistribute it
 *     and/or modify it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation, either version 3 of
 *     the License, or any later version.
 *
 *     The aion network project is distributed in the hope that it will
 *     be useful, but WITHOUT ANY WARRANTY; without even the implied
 *     warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *     See the GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with the aion network project source files.
 *     If not, see <https://www.gnu.org/licenses/>.
 *
 * Contributors:
 *     Aion foundation.
 */

package org.aion.mcf.valid;


import java.util.List;
import org.aion.mcf.blockchain.valid.IValidRule;
import org.slf4j.Logger;

public abstract class AbstractBlockHeaderValidator {

    public void logErrors(final Logger logger,
        final List<IValidRule.RuleError> errors) {
        if (errors.isEmpty()) {
            return;
        }

        if (logger.isErrorEnabled()) {
            StringBuilder builder = new StringBuilder();
            builder.append(this.getClass().getSimpleName());
            builder.append(" raised errors: \n");
            for (IValidRule.RuleError error : errors) {
                builder.append(error.errorClass.getSimpleName());
                builder.append("\t\t\t\t");
                builder.append(error.error);
                builder.append("\n");
            }
            logger.error(builder.toString());
        }
    }
}
