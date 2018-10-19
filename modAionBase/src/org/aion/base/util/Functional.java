/**
 * ***************************************************************************** Copyright (c)
 * 2017-2018 Aion foundation.
 *
 * <p>This file is part of the aion network project.
 *
 * <p>The aion network project is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or any later version.
 *
 * <p>The aion network project is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
 * PURPOSE. See the GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU General Public License along with the aion network
 * project source files. If not, see <https://www.gnu.org/licenses/>.
 *
 * <p>The aion network project leverages useful source code from other open source projects. We
 * greatly appreciate the effort that was invested in these projects and we thank the individual
 * contributors for their work. For provenance information and contributors please see
 * <https://github.com/aionnetwork/aion/wiki/Contributors>.
 *
 * <p>Contributors to the aion source files in decreasing order of code volume: Aion foundation.
 * <ether.camp> team through the ethereumJ library. Ether.Camp Inc. (US) team through Ethereum
 * Harmony. John Tromp through the Equihash solver. Samuel Neves through the BLAKE2 implementation.
 * Zcash project team. Bitcoinj team.
 * ****************************************************************************
 */
package org.aion.base.util;

public interface Functional {

    /**
     * Represents an operation that accepts a single input argument and returns no result. Unlike
     * most other functional interfaces, {@code Consumer} is expected to operate via side-effects.
     *
     * @param <T> the type of the input to the operation
     */
    interface Consumer<T> {

        /**
         * Performs this operation on the given argument.
         *
         * @param t the input argument
         */
        void accept(T t);
    }

    /**
     * Represents an operation that accepts two input arguments and returns no result. This is the
     * two-arity specialization of {@link java.util.function.Consumer}. Unlike most other functional
     * interfaces, {@code BiConsumer} is expected to operate via side-effects.
     *
     * @param <T> the type of the first argument to the operation
     * @param <U> the type of the second argument to the operation
     * @see org.ethereum.util.Functional.Consumer
     */
    interface BiConsumer<T, U> {

        /**
         * Performs this operation on the given arguments.
         *
         * @param t the first input argument
         * @param u the second input argument
         */
        void accept(T t, U u);
    }

    /**
     * Represents a function that accepts one argument and produces a result.
     *
     * @param <T> the type of the input to the function
     * @param <R> the type of the result of the function
     */
    interface Function<T, R> {

        /**
         * Applies this function to the given argument.
         *
         * @param t the function argument
         * @return the function result
         */
        R apply(T t);
    }

    interface Supplier<T> {

        /**
         * Gets a result.
         *
         * @return a result
         */
        T get();
    }

    interface InvokeWrapper {

        void invoke();
    }

    interface InvokeWrapperWithResult<R> {

        R invoke();
    }

    interface Predicate<T> {

        boolean test(T t);
    }
}
