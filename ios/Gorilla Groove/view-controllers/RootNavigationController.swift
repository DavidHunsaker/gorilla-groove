import Foundation
import UIKit

class RootNavigationController : UIViewController {
    
    let libraryController = MyLibraryController()
    let usersController = UsersController()
    let playlistsController = PlaylistsController()
    
    lazy var topView = UINavigationController()
    lazy var activeButton: NavigationButton? = nil
    
    lazy var myLibraryButton = NavigationButton("My Library", "music.house.fill", libraryController, handleButtonTap)
    lazy var nowPlayingButton = NavigationButton("Now Playing", "music.note", nil, handleButtonTap)
    lazy var usersButton = NavigationButton("Users", "person.3.fill", usersController, handleButtonTap)
    lazy var playlistsButton = NavigationButton("Playlists", "music.note.list", playlistsController, handleButtonTap)
    lazy var settingsButton = NavigationButton("Settings", "gear", nil, handleButtonTap)
    
    lazy var buttons = [myLibraryButton, nowPlayingButton, usersButton, playlistsButton, settingsButton]
    
    
    override func viewDidLoad() {
        print("Loaded root navigation")
        super.viewDidLoad()
        topView.pushViewController(libraryController, animated: false)
        
        activeButton = myLibraryButton
        myLibraryButton.setActive()
        
        let stackView = UIStackView()
        stackView.translatesAutoresizingMaskIntoConstraints = false
        stackView.axis = .vertical
        stackView.distribution  = .fill
        stackView.alignment = .fill
        
        let mediaControls = MediaControlsController()
        let middleBar = createMiddleBar()
        let navigationControls = createNavigationControls()
        
        stackView.addArrangedSubview(topView.view)
        stackView.addArrangedSubview(mediaControls.view)
        stackView.addArrangedSubview(middleBar)
        stackView.addArrangedSubview(navigationControls)
        
        self.view.backgroundColor = Colors.primary
        self.view.translatesAutoresizingMaskIntoConstraints = false
        
        self.view.addSubview(stackView)
        self.addChild(topView)
        
        topView.view.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            stackView.widthAnchor.constraint(equalTo: self.view.widthAnchor),
            stackView.heightAnchor.constraint(equalTo: self.view.heightAnchor),
        ])
    }
    
    override func viewDidAppear(_ animated: Bool) {
        UserState().postCurrentDevice()
        ServerSynchronizer().syncWithServer()
    }
    
    private func createNavigationControls() -> UIView {
        let view = UIView()
        
        let stackView = UIStackView()
        stackView.axis = .horizontal
        stackView.distribution  = .fillEqually
        stackView.translatesAutoresizingMaskIntoConstraints = false
        
        buttons.forEach { button in
            stackView.addArrangedSubview(button.view)
        }
        
        view.addSubview(stackView)
        view.backgroundColor = Colors.primary
        
        NSLayoutConstraint.activate([
            stackView.leftAnchor.constraint(equalTo: view.leftAnchor, constant: 8),
            stackView.rightAnchor.constraint(equalTo: view.rightAnchor, constant: -8),
            stackView.bottomAnchor.constraint(equalTo: view.bottomAnchor, constant: -8),
            view.heightAnchor.constraint(equalToConstant: 70)
        ])
        
        return view
    }
    
    private func createMiddleBar() -> UIView {
        let view = UIView()
        view.translatesAutoresizingMaskIntoConstraints = false
        view.backgroundColor = Colors.disabledWhite
        
        view.heightAnchor.constraint(equalToConstant: 1).isActive = true
        
        return view
    }
    
    private func handleButtonTap(_ tappedButton: NavigationButton) {
        // Do some extra tomfoolery here so that the swipe gesture (left or right) matches
        // where the tapped button was in relation to our currently active button
        let currentViewIndex = buttons.firstIndex { button in
            button == activeButton
        }
        let nextViewIndex = buttons.firstIndex { button in
            button.controllerToLoad == tappedButton.controllerToLoad
        }
        
        if (currentViewIndex == nextViewIndex) {
            return // Tapped the same button that was already active. Nothing to do
        }
        
        let newViews: Array<UIViewController> = {
            if (currentViewIndex != nil && currentViewIndex! > nextViewIndex!) {
                return [tappedButton.controllerToLoad!, topView.topViewController!]
            } else {
                return [tappedButton.controllerToLoad!]
            }
        }()
        
        topView.setViewControllers(newViews, animated: true)
        topView.popToRootViewController(animated: true)
        
        activeButton!.setInactive()
        activeButton = tappedButton
        tappedButton.setActive()
    }
    
    class NavigationButton : UIViewController {
        let label: UILabel = UILabel()
        var swapViewFunction: ((NavigationButton) -> ())? = nil
        var icon: UIImageView = UIImageView()
        var controllerToLoad: UIViewController? = nil
        
        override func viewDidLoad() {
            let stackView = UIStackView()
            stackView.translatesAutoresizingMaskIntoConstraints = false
            stackView.axis = .vertical
            stackView.alignment = .center
            
            label.font = UIFont.systemFont(ofSize: 12)
            // Hacky, but the button has already been set active before it loads and this stomps out the new color
            if (label.textColor != Colors.secondary) {
                label.textColor = Colors.whiteTransparent
            }
            
            stackView.addArrangedSubview(icon)
            stackView.addArrangedSubview(label)
            
            stackView.setCustomSpacing(8.0, after: icon)
            
            self.view.addGestureRecognizer(UITapGestureRecognizer(
                target: self,
                action: #selector(switchToView(sender:))
            ))
            
            self.view.addSubview(stackView)
            self.view.translatesAutoresizingMaskIntoConstraints = false
            
            NSLayoutConstraint.activate([
                stackView.widthAnchor.constraint(equalTo: self.view.widthAnchor),
                stackView.heightAnchor.constraint(equalTo: self.view.heightAnchor)
            ])
        }
        
        init(
            _ labelText: String,
            _ iconName: String,
            _ controllerToLoad: UIViewController?,
            _ swapViewFunction: @escaping (NavigationButton) -> ()
        ) {
            self.label.text = labelText
            self.controllerToLoad = controllerToLoad
            self.swapViewFunction = swapViewFunction
            
            super.init(nibName: nil, bundle: nil)
            self.icon = createIcon(iconName)
        }
        
        private func createIcon(_ name: String) -> UIImageView {
            let config = UIImage.SymbolConfiguration(pointSize: UIFont.systemFontSize * 1.2, weight: .medium, scale: .large)
            
            let icon = UIImageView(image: UIImage(systemName: name, withConfiguration: config)!)
            icon.isUserInteractionEnabled = true
            icon.tintColor = Colors.whiteTransparent
            
            return icon
        }
        
        @objc func switchToView(sender: UITapGestureRecognizer) {
            if (controllerToLoad != nil) {
                swapViewFunction!(self)
            }
        }
        
        func setActive() {
            label.textColor = Colors.secondary
            icon.tintColor = Colors.secondary
        }
        
        func setInactive() {
            label.textColor = Colors.whiteTransparent
            icon.tintColor = Colors.whiteTransparent
        }
        
        required init?(coder aDecoder: NSCoder) {
            super.init(coder: aDecoder)
        }
    }
    
}
