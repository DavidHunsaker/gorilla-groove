import UIKit
import Foundation

class ArtistViewController: UIViewController, UITableViewDataSource, UITableViewDelegate {
    private var artists: [String] = []
    private var visibleArtists: [String] = []
    private let originalView: LibraryViewType?
    
    private lazy var filterOptions: [[FilterOption]] = [
        MyLibraryHelper.getNavigationOptions(vc: self, viewType: .ARTIST),
    ]
    
    private lazy var filter = TableFilter(filterOptions, vc: self)
    
    override func viewDidLoad() {
        super.viewDidLoad()
        GGNavLog.info("Loaded artist view")
        
        let artistTableView = UITableView()
        
        view.addSubview(artistTableView)
        
        artistTableView.translatesAutoresizingMaskIntoConstraints = false
        
        NSLayoutConstraint.activate([
            artistTableView.topAnchor.constraint(equalTo:view.topAnchor),
            artistTableView.leftAnchor.constraint(equalTo:view.leftAnchor),
            artistTableView.rightAnchor.constraint(equalTo:view.rightAnchor),
            artistTableView.bottomAnchor.constraint(equalTo:view.bottomAnchor),
        ])
        filter.topAnchor.constraint(equalTo: view.topAnchor).isActive = true
        filter.rightAnchor.constraint(equalTo: view.rightAnchor, constant: -10).isActive = true
        
        artistTableView.dataSource = self
        artistTableView.delegate = self
        artistTableView.register(ArtistViewCell.self, forCellReuseIdentifier: "artistCell")
        
        TableSearchAugmenter.addSearchToNavigation(
            controller: self,
            tableView: artistTableView,
            onTap: { [weak self] in self?.filter.setIsHiddenAnimated(true) }
        ) { [weak self] input in
            guard let this = self else { return }
            
            let searchTerm = input.lowercased()
            if (searchTerm.isEmpty) {
                this.visibleArtists = this.artists
            } else {
                this.visibleArtists = this.artists.filter { $0.lowercased().contains(searchTerm) }
            }
        }
        
        // Remove extra table rows when we don't have a full screen of songs
        artistTableView.tableFooterView = UIView(frame: .zero)
    }
    
    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        return visibleArtists.count
    }
    
    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = tableView.dequeueReusableCell(withIdentifier: "artistCell", for: indexPath) as! ArtistViewCell
        let artist = visibleArtists[indexPath.row]
        
        cell.artist = artist
        
        let tapGesture = UITapGestureRecognizer(target: self, action: #selector(handleTap(sender:)))
        cell.addGestureRecognizer(tapGesture)
        
        return cell
    }
    
    func scrollViewWillBeginDragging(_ scrollView: UIScrollView) {
        filter.setIsHiddenAnimated(true)
    }
    
    @objc private func handleTap(sender: UITapGestureRecognizer) {
        let cell = sender.view as! ArtistViewCell
        
        cell.animateSelectionColor()
        
        let albums = TrackDao.getAlbums(artist: cell.artist!, isSongCached: OfflineStorageService.offlineModeEnabled ? true : nil)
        
        // If we only have one album to view, may as well just load it and save ourselves a tap
        let view: UIViewController = {
            if (albums.count == 1) {                
                return TrackViewController(albums.first!.name, originalView: .ARTIST, artistFilter: cell.artist!, albumFilter: albums.first!.name)
            } else {
                return AlbumViewController(cell.artist!, albums, cell.artist!)
            }
        }()
        
        navigationController!.pushViewController(view, animated: true)
    }
    
    init(_ title: String, _ artists: [String], originalView: LibraryViewType? = nil) {
        self.artists = artists
        self.visibleArtists = artists
        self.originalView = originalView
        
        super.init(nibName: nil, bundle: nil)

        self.title = title
    }
    
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
}
