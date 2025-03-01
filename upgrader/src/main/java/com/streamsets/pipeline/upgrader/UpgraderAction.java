/*
 * Copyright 2019 StreamSets Inc.
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
package com.streamsets.pipeline.upgrader;

import com.streamsets.pipeline.api.Config;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;


public abstract class UpgraderAction<U extends UpgraderAction, T> {

  protected interface ConfigsAdapter extends Iterable<ConfigsAdapter.Pair> {
    interface Pair {
      String getName();
      Object getValue();
    }

    ConfigsAdapter.Pair find(String name);
    ConfigsAdapter.Pair set(String name, Object value);
    ConfigsAdapter.Pair remove(String name);

    @NotNull
    @Override
    Iterator<Pair> iterator();

  }

  private static class ListConfigConfigsAdapter implements ConfigsAdapter {

    private static class PairImpl implements Pair {
      private final Config config;

      public PairImpl(Config config) {
        this.config = config;
      }

      @Override
      public String getName() {
        return config.getName();
      }

      @Override
      public Object getValue() {
        return config.getValue();
      }

    }

    private final List<Config> configs;

    public ListConfigConfigsAdapter(Object configs) {
      this.configs = (List<Config>) configs;
    }

    protected int findIndexOf(List<Config> configs, String name) {
      for (int i = 0; i < configs.size(); i++) {
        if (name.equals(configs.get(i).getName())) {
          return i;
        }
      }
      return -1;
    }

    @Override
    public ConfigsAdapter.Pair find(String name) {
      int configIdx = findIndexOf(configs, name);
      return (configIdx == -1) ? null : new PairImpl(configs.get(configIdx));
    }

    @Override
    public ConfigsAdapter.Pair set(String name, Object value) {
      int configIdx = findIndexOf(configs, name);
      Config config = new Config(name, value);
      if (configIdx == -1) {
        configs.add(config);
      } else {
        configs.set(configIdx, config);
      }
      return new PairImpl(config);
    }

    @Override
    public ConfigsAdapter.Pair remove(String name) {
      int configIdx = findIndexOf(configs, name);
      Config config = (configIdx == -1) ? null : configs.remove(configIdx);
      return (config == null) ? null : new PairImpl(config);
    }

    @NotNull
    @Override
    public Iterator<Pair> iterator() {
      return configs.stream().map(c -> (ConfigsAdapter.Pair) new PairImpl(c)).collect(Collectors.toList()).iterator();
    }

  }

  private static class MapConfigsAdapter implements ConfigsAdapter {

    private static class PairImpl implements Pair {
      private final String name;
      private final Object value;

      public PairImpl(String name, Object value) {
        this.name = name;
        this.value = value;
      }

      @Override
      public String getName() {
        return name;
      }

      @Override
      public Object getValue() {
        return value;
      }

    }

    private final Map<String, Object> configs;

    public MapConfigsAdapter(Object configs) {
      this.configs = (Map<String, Object>) configs;
    }

    @Override
    public ConfigsAdapter.Pair find(String name) {
      Object value = configs.get(name);
      return (configs.containsKey(name)) ? new PairImpl(name, value) : null;
    }

    @Override
    public ConfigsAdapter.Pair set(String name, Object value) {
      configs.put(name, value);
      return new PairImpl(name, value);
    }

    @Override
    public ConfigsAdapter.Pair remove(String name) {
      Object value = configs.remove(name);
      return (configs.containsKey(name)) ? new PairImpl(name, value) : null;
    }

    @NotNull
    @Override
    public Iterator<Pair> iterator() {
      return configs.entrySet()
          .stream()
          .map(e -> (ConfigsAdapter.Pair) new PairImpl(e.getKey(), e.getValue()))
          .collect(Collectors.toList())
          .iterator();
    }

  }

  public static final Function<List<Config>, ConfigsAdapter> CONFIGS_WRAPPER = ListConfigConfigsAdapter::new;

  public static final Function<Map<String, Object>, ConfigsAdapter> MAP_WRAPPER = MapConfigsAdapter::new;

  private final Function<T, ConfigsAdapter> wrapper;
  private String name;

  public UpgraderAction(Function<T, ConfigsAdapter> wrapper) {
    this.wrapper = wrapper;
  }

  protected ConfigsAdapter wrap(T configs) {
    return wrapper.apply(configs);
  }

  public abstract void upgrade(T configs);

  public String getName() {
    return name;
  }

  public U setName(String name) {
    this.name = name;
    return (U) this;
  }

}
