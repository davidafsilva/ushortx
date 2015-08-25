package pt.davidafsilva.ushortx.http;

import org.hashids.Hashids;

import java.util.Optional;

/**
 * The hash utility class for generating and reverting hashes from and to integer. This is achieved
 * by using the {@link Hashids} facility.
 *
 * @author David Silva
 */
public class Hash {

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
    final long[] ids = getHashFacility(salt).decode(hash);
    return (ids == null || ids.length != 1) ? Optional.empty() : Optional.of(ids[0]);
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
