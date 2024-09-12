
/*
 *
 *  * Copyright (c) Crio.Do 2019. All rights reserved
 *
 */

package com.crio.qeats.repositories;

import com.crio.qeats.models.MenuEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface MenuRepository extends MongoRepository<MenuEntity, String> {

  Optional<MenuEntity> findMenuByRestaurantId(String restaurantId);

  Optional<List<MenuEntity>> findMenusByItemsItemIdIn(List<String> itemIdList);

  @Query("{'items.name': ?0}")
  public Optional<List<MenuEntity>> findMenusByItemNameExact(String name);

  @Query("{'items': { $elemMatch: { $regex: ?0, $options: 'i' } } }")
  Optional<List<MenuEntity>> findMenusByItemName(String searchString);

}
