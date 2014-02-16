package sandbox;

import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.nio.charset.spi.CharsetProvider;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.cliffc.high_scale_lib.NonBlockingHashMap;

import com.google.common.collect.ImmutableMap;

/**
 * NonBlockingCharsetProvider to workaround the contention point on
 * {@link CharsetProvider#charsetForName(String)}
 * 
 * @author Leo Lewis
 * @see java.nio.charset.spi.CharsetProvider
 * @see java.nio.charset.Charset
 */
public class NonBlockingCharsetProvider extends CharsetProvider {

	private CharsetProvider parent;

	private boolean lazyInit;

	private Map<String, Charset> cache;

	/**
	 * @param parent
	 *            parent charset provider
	 * @param lazyInit
	 *            if lazy init, init the cache when the application needs the
	 *            charset, otherwise populate with the parent in the constructor
	 *            if lazy init, will use a ConcurrentMap as it might be changed
	 *            and iterated concurrently, otherwise, will use a
	 *            guava Immutablehashmap
	 */
	public NonBlockingCharsetProvider(final CharsetProvider parent, final boolean lazyInit) {
		this.parent = parent;
		this.lazyInit = lazyInit;
		if (!lazyInit) {
			Map<String, Charset> tmp = new HashMap<>();
			Iterator<Charset> it = parent.charsets();
			while (it.hasNext()) {
				Charset charset = it.next();
				tmp.put(charset.name(), charset);
			}
			cache = ImmutableMap.copyOf(tmp);
		} else {
			cache = new NonBlockingHashMap<>();
		}
	}

	@Override
	public Charset charsetForName(final String name) {
		Charset charset = null;
		// if not lazyInit, the value should already be in the cache
		if (lazyInit && !cache.containsKey(name)) {
			// no lock here, so we might call several times the parent and put
			// the entry into the cache, it doesn't matter as the cache will be
			// populated eventually and we won't have to call the parent anymore
			charset = parent.charsetForName(name);
			cache.put(name, charset);
		}
		return cache.get(name);
	}

	@Override
	public Iterator<Charset> charsets() {
		if (lazyInit) {
			return parent.charsets();
		}
		return cache.values().iterator();
	}

	/**
	 * Save it if we want to reinstall, set up several times the provider
	 */
	private static CharsetProvider standardProvider;

	/**
	 * Replace the CharsetProvider into the Charset class by an instance of this
	 * {@link NonBlockingCharsetProvider}
	 * 
	 * @param lazyInit
	 *            see
	 *            {@link NonBlockingCharsetProvider#NonBlockingCharsetProvider(CharsetProvider, boolean)}
	 */
	public static void setUp(boolean lazyInit) throws Exception {
		Field field = Charset.class.getDeclaredField("standardProvider");
		field.setAccessible(true);
		if (standardProvider == null) {
			standardProvider = (CharsetProvider) field.get(null);
		}
		NonBlockingCharsetProvider nonBlocking = new NonBlockingCharsetProvider(standardProvider,
				lazyInit);
		field.set(null, nonBlocking);
	}

	/**
	 * Restore the default java provider
	 * 
	 * @throws Exception
	 */
	public static void uninstall() throws Exception {
		if (standardProvider != null) {
			Field field = Charset.class.getDeclaredField("standardProvider");
			field.setAccessible(true);
			field.set(null, standardProvider);
		}
	}
}
