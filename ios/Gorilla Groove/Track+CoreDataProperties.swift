//
//  Track+CoreDataProperties.swift
//  Gorilla Groove
//
//  Created by mobius-mac on 1/11/20.
//  Copyright © 2020 mobius-mac. All rights reserved.
//
//

import Foundation
import CoreData


extension Track {

    @nonobjc public class func fetchRequest() -> NSFetchRequest<Track> {
        return NSFetchRequest<Track>(entityName: "Track")
    }

    @NSManaged public var album: String?
    @NSManaged public var artist: String?
    @NSManaged public var created_at: NSDate?
    @NSManaged public var featuring: String?
    @NSManaged public var genre: String?
    @NSManaged public var id: Int64
    @NSManaged public var is_hidden: Bool
    @NSManaged public var is_private: Bool
    @NSManaged public var last_played: NSDate?
    @NSManaged public var length: Int16
    @NSManaged public var name: String?
    @NSManaged public var note: String?
    @NSManaged public var play_count: Int16
    @NSManaged public var release_year: Int16
    @NSManaged public var track_number: Int16
    @NSManaged public var updated_at: NSDate?

}
