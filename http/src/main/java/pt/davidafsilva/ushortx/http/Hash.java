package pt.davidafsilva.ushortx.http;

/*
 * #%L
 * ushortx-http
 * %%
 * Copyright (C) 2015 David Silva
 * %%
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * #L%
 */

import org.hashids.Hashids;

import java.util.Optional;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

/**
 * The hash utility class for generating and reverting hashes from and to integer. This is achieved
 * by using the {@link Hashids} facility.
 *
 * @author David Silva
 */
public final class Hash {

  // the logger
  private static final Logger LOGGER = LoggerFactory.getLogger(Hash.class);


  // the hash facility reference
  // volatile here is required due to our locking/singleton instance retrieval/instantiation
  private static volatile Hashids hashFacility;

  /**
   * Generates an unique hash the specified identifier
   *
   * @param salt the algorithm's salt
   * @param id   the id for which to generate the hash
   * @return the generated hash
   */
  public static String generate(final String salt, final long id) {
    return getHashFacility(salt).encode(id);
  }

  /**
   * Reveres a previously generated hash with the given salt. If the specified hash was not
   * generated with the given salt or another error occurs, an {@link Optional#empty()} reference
   * is returned.
   *
   * @param salt the original salt that was used for the hash generation
   * @param hash the hash to be reversed
   * @return the original identifier, if applicable
   */
  public static Optional<Long> reverse(final String salt, final String hash) {
    try {
      final long[] ids = getHashFacility(salt).decode(hash);
      return (ids == null || ids.length != 1) ? Optional.empty() : Optional.of(ids[0]);
    } catch (final Exception e) {
      LOGGER.warn("invalid hash provided");
      // invalid hash provided
      return Optional.empty();
    }
  }

  /**
   * Returns a valid instance of the hash facility.
   * This method is thread-safe and return always the same instance.
   *
   * @param salt the salt for the hash facility
   * @return the hash facility instance
   */
  private static Hashids getHashFacility(final String salt) {
    if (hashFacility == null) {
      synchronized (Hash.class) {
        if (hashFacility == null) {
          hashFacility = new Hashids(salt);
        }
      }
    }
    return hashFacility;
  }
}
